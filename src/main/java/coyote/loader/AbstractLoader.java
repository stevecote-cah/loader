/*
 * Copyright (c) 2015 Stephan D. Cote' - All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the MIT License which accompanies this distribution, and is 
 * available at http://creativecommons.org/licenses/MIT/
 *
 * Contributors:
 *   Stephan D. Cote 
 *      - Initial concept and initial implementation
 */
package coyote.loader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import coyote.commons.GUID;
import coyote.commons.StringUtil;
import coyote.dataframe.DataField;
import coyote.dataframe.DataFrame;
import coyote.loader.cfg.Config;
import coyote.loader.cfg.ConfigurationException;
import coyote.loader.component.ManagedComponent;
import coyote.loader.log.Log;
import coyote.loader.log.LogMsg;
import coyote.loader.log.Logger;
import coyote.loader.thread.Scheduler;
import coyote.loader.thread.ThreadJob;
import coyote.loader.thread.ThreadPool;


/**
 * 
 */
public abstract class AbstractLoader extends ThreadJob implements Loader, Runnable {

  /** A map of all the component configurations keyed by their instance */
  protected final HashMap<Object, Config> components = new HashMap<Object, Config>();

  /** A map of components to when they last checked in...helps detect hung components. */
  protected final HashMap<Object, Long> checkin = new HashMap<Object, Long>();

  /** A map of components to the interval to when they should be considered hung and should be restarted. */
  protected final HashMap<Object, Long> hangtime = new HashMap<Object, Long>();

  /** The time to pause (sleep) between idle loop cycles (dflt=3000ms) */
  protected long parkTime = 3000;

  /** Our configuration */
  protected Config configuration = new Config();

  /** Our very simple thread pool */
  protected final ThreadPool threadpool = new ThreadPool();

  protected Scheduler scheduler = null;




  /**
   * @see coyote.loader.Loader#configure(coyote.loader.cfg.Config)
   */
  @Override
  public void configure( Config cfg ) throws ConfigurationException {
    configuration = cfg;

    // setup logging as soon as we can
    initLogging();
  }




  private void initLogging() {
    List<Config> loggers = configuration.getSections( ConfigTag.LOGGER );

    Logger retval = null;
    for ( Config cfg : loggers ) {

      // Look for the class to load
      for ( DataField field : cfg.getFields() ) {
        if ( ConfigTag.CLASS.equalsIgnoreCase( field.getName() ) ) {
          String className = field.getStringValue();
          if ( className != null && StringUtil.countOccurrencesOf( className, "." ) < 1 ) {
            className = Log.class.getPackage().getName() + "." + className;
          }

          try {
            Class<?> clazz = Class.forName( className );
            Constructor<?> ctor = clazz.getConstructor();
            Object object = ctor.newInstance();

            if ( object instanceof Logger ) {
              retval = (Logger)object;
              retval.setConfig( cfg );

              // Get the name of the logger
              String name = cfg.getString( ConfigTag.NAME );

              // If there is no name, try looking for an ID
              if ( StringUtil.isBlank( name ) ) {
                name = cfg.getString( ConfigTag.ID );
              }

              //If no name or ID, assign it a name
              if ( Log.isBlank( name ) ) {
                name = GUID.randomGUID().toString();
              }

              // Add the logger to the logging subsystem and initialize it
              Log.addLogger( name, retval );

            } else {
              System.err.println( LogMsg.createMsg( "Loader.class_is_not_logger", className ) );
              System.exit( 11 );
            }
          } catch ( ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e ) {
            System.err.println( LogMsg.createMsg( "Loader.logger_instantiation_error", className, e.getClass().getName(), e.getMessage() ) );
            System.exit( 10 );
          }
        }
      }

    }

    Log.info( LogMsg.createMsg( "Loader.logging_initiated", new Date() ) );

  }




  /**
   * Cycle through the configuration and load all the components defined 
   * therein.
   * 
   * <p>This looks for a section named {@code Components} or {@code Component} 
   * and treat each section as a component configuration. This will of course 
   * require at least one attribute ({@code Class}) which defines the class of 
   * the object to load and configure.</p>
   */
  protected void initComponents() {
    List<Config> sections = configuration.getSections( ConfigTag.COMPONENTS );

    // Look for the COMPONENTS section
    for ( Config section : sections ) {
      // get each of the configurations
      for ( Config cfg : section.getSections() ) {
        loadComponent( cfg );
      }
    }

    // Look for the singular version of the attribute
    sections = configuration.getSections( ConfigTag.COMPONENT );
    for ( Config section : sections ) {
      for ( Config cfg : section.getSections() ) {
        loadComponent( cfg );
      }
    }

    // For each of the components, check to see if it is to be run in the threadpool

  }




  /**
   * This will use the configuration to load and configure the component
   * 
   * <p>This is normally called in two locations: when the loader first runs 
   * (from {@link #initComponents()}) and in the {@link #watchdog()} method 
   * which will shutdown an inactive / hung component and restart a fresh one 
   * in its place.</p>
   *   
   * @param config The configuration of the component to load
   */
  private void loadComponent( Config config ) {

    String className = config.getString( ConfigTag.CLASS );

    // Create the component
    if ( StringUtil.isNotBlank( className ) ) {

      try {
        Class<?> clazz = Class.forName( className );
        Constructor<?> ctor = clazz.getConstructor();
        Object object = ctor.newInstance();

        if ( object instanceof ManagedComponent ) {
          ManagedComponent retval = (ManagedComponent)object;
          retval.setConfiguration( config );

          // Set this loader as the watchdog if the component is interested 
          retval.setLoader( this );

          // Add it to the components map
          components.put( object, config );

        } else {
          System.err.println( LogMsg.createMsg( "Loader.class_is_not_logic_component", className ) );
          System.exit( 9 );
        }
      } catch ( ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e ) {
        System.err.println( LogMsg.createMsg( "Loader.component_instantiation_error", className, e.getClass().getName(), e.getMessage() ) );
        System.exit( 8 );
      }
    }

  }




  /**
   * 
   */
  protected void terminateComponents() {
    DataFrame frame = new DataFrame();
    frame.put( "Message", "Normal termination" );

    for ( final Iterator<Object> it = components.keySet().iterator(); it.hasNext(); ) {
      final Object cmpnt = it.next();
      if ( cmpnt instanceof ManagedComponent ) {
        safeShutdown( (ManagedComponent)cmpnt, frame );
      } else if ( cmpnt instanceof ThreadJob ) {
        ( (ThreadJob)cmpnt ).shutdown(); // May not work as expected
      }

      // remove the component
      it.remove();
    }
  }




  /**
   * @return the parkTime
   */
  public long getParkTime() {
    return parkTime;
  }




  /**
   * @param parkTime the parkTime to set
   */
  public void setParkTime( long parkTime ) {
    this.parkTime = parkTime;
  }




  /**
   * Shut the component down in a separate thread.
   * 
   * <p>This is a way to ensure that the calling thread does not get hung in a
   * deadlocked component while trying to shutdown a component.</p>
   * 
   * @param cmpnt the managed component to terminate
   * @param frame the dataframe which contains any additional information 
   *        relating to the shutdown request. This may be null.
   *
   * @return the Thread in which the shutdown is occurring.
   */
  protected Thread safeShutdown( final ManagedComponent cmpnt, final DataFrame frame ) {
    final Thread closer = new Thread( new Runnable() {
      public void run() {
        cmpnt.shutdown( frame );
      }
    } );

    closer.start();

    // give the component a chance to wake up and terminate
    Thread.yield();

    return closer;
  }




  /**
   * The main execution loop.
   * 
   * <p>This is where the thread spends its time monitoring components it has 
   * loaded and performing housekeeping operations.</p>
   * 
   * <p>While it is called a watchdog, this does not detect when a component is 
   * hung. The exact API for components to "pet the dog" is still in the 
   * works.</p>
   */
  protected void watchdog() {
    setActiveFlag( true );

    Log.info( LogMsg.createMsg( "Loader.operational" ) );

    while ( !isShutdown() ) {

      // Make sure that all this loaders are active, otherwise remove the
      // reference to them and allow GC to remove them from memory
      synchronized( components ) {
        for ( final Iterator<Object> it = components.keySet().iterator(); it.hasNext(); ) {
          final Object cmpnt = it.next();
          if ( cmpnt instanceof ManagedComponent ) {
            if ( !( (ManagedComponent)cmpnt ).isActive() ) {
              Log.info( LogMsg.createMsg( "Loader.removing_inactive_cmpnt", cmpnt.toString() ) );

              // get a reference to the components configuration
              final Config config = components.get( cmpnt );

              // communicate the reason for the shutdown
              DataFrame frame = new DataFrame();
              frame.put( "Message", "Terminating due to inactivity" );

              // try to shut it down properly
              safeShutdown( (ManagedComponent)cmpnt, frame );

              // remove the component
              it.remove();

              // re-load the component
              loadComponent( config );
            }
          }
        }
      }

      // TODO cycle through all the hangtime objects and check their last 
      // check-in time. If expired, log the event and restart them like the 
      // above active check

      // Monitor check-in map size; if it is too large, we have a problem
      if ( checkin.size() > components.size() ) {
        Log.fatal( LogMsg.createMsg( "Loader.check_in_map_size", checkin.size(), components.size() ) );
      }

      // If we have no components which are active, there is not need for this
      // loader to remain running
      if ( components.size() == 0 ) {
        Log.warn( LogMsg.createMsg( "Loader.no_components" ) );
        this.shutdown();
      }

      // Yield to other threads and sleep(wait) for a time
      park( parkTime );

    }

    if ( Log.isLogging( Log.DEBUG_EVENTS ) ) {
      Log.debug( LogMsg.createMsg( "Loader.terminating" ) );
    }

    terminate();

    setActiveFlag( false );
  }




  /**
   * @see coyote.loader.Loader#checkIn(java.lang.Object)
   */
  @Override
  public void checkIn( Object component ) {
    checkin.put( component, new Long( System.currentTimeMillis() ) );
  }




  /**
   * @see coyote.loader.WatchDog#setHangTime(long, java.lang.Object, coyote.loader.cfg.Config)
   */
  @Override
  public void setHangTime( long millis, Object component, Config cfg ) {
    // TODO Auto-generated method stub
  }




  /**
   * @see coyote.loader.Loader#getWatchdog()
   */
  @Override
  public WatchDog getWatchdog() {
    return this;
  }




  /**
   * @see coyote.loader.Loader#getScheduler()
   */
  @Override
  public synchronized Scheduler getScheduler() {
    if ( scheduler == null ) {
      scheduler = new Scheduler();
      try {
        threadpool.handle( scheduler );
      } catch ( InterruptedException e ) {
        Log.append( Log.WARN, LogMsg.createMsg( "Loader.scheduler_creation_error", e.getClass().getName(), e.getMessage() ) );
        scheduler = null;
      }
    }
    return scheduler;
  }




  /**
   * @see coyote.loader.Loader#start()
   */
  @Override
  public void start() {
    // do-nothing implementation
  }




  /**
   * @see coyote.loader.Loader#getThreadPool()
   */
  @Override
  public ThreadPool getThreadPool() {
    return threadpool;
  }

}
