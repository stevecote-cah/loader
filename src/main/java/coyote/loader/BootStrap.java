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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Date;

import coyote.commons.CipherUtil;
import coyote.commons.ExceptionUtil;
import coyote.commons.FileUtil;
import coyote.commons.StringUtil;
import coyote.commons.UriUtil;
import coyote.dataframe.DataField;
import coyote.loader.cfg.Config;
import coyote.loader.cfg.ConfigurationException;
import coyote.loader.log.Log;
import coyote.loader.log.LogMsg;
import coyote.loader.log.LogMsg.BundleBaseName;


/**
 * This is a boot strap loader which uses the configuration file to determine 
 * which loader to use.
 * 
 * <p>Since there are several different loaders from which to choose, this 
 * loader reads in a configuration, like other loaders, then uses the 
 * configuration to determine which loader to use.</p>
 */
public class BootStrap extends AbstractLoader {

  private static Config configuration = null;
  private static URI cfgUri = null;

  static {
    LogMsg.setBundleBaseNameDefault( new BundleBaseName( "LoaderMsg" ) );
  }




  /**
   * Performs encryption operation from the command line arguments.
   * 
   * <p>The loader provides a way for the operator to generate encrypted values 
   * which can be placed in configuration files. This allows user names and 
   * passwords to be hidden from those with access to the files but who do not 
   * have access to the encryption keys.</p>
   * 
   * @param args the entire command line arguments to parse for encryption details
   */
  private static void encrypt( String[] args ) {
    String token = null;
    String key = System.getProperty( ConfigTag.CIPHER_KEY, CipherUtil.getKey( CIPHER_KEY ) );
    String cipherName = System.getProperty( ConfigTag.CIPHER_NAME, CIPHER_NAME );
    if ( args.length < 2 ) {
      System.err.println( "Nothing to encrypt" );
      return;
    } else {
      token = args[1];
      if ( args.length > 2 ) {
        String rawkey = args[2];
        // make sure is it base64 encoded or make it so
        try {
          CipherUtil.decode( rawkey );
          key = rawkey;
        } catch ( Exception e ) {
          System.out.println( "User-specified key did not appear to be Base64 encoded, encoding it." );
          key = CipherUtil.getKey( rawkey );
        }

        if ( args.length > 3 ) {
          cipherName = args[3];
        }

      }
    }
    System.out.println( "Encrypting '" + token + "'" );
    System.out.println( "with a key of '" + key + "'" );
    System.out.println( "using a '" + cipherName + "' cipher" );

    if ( CipherUtil.getCipher( cipherName ) != null ) {
      String ciphertext = CipherUtil.encipher( token, cipherName, key );
      System.out.println( ciphertext );
    } else {
      System.err.println( "Cipher '" + cipherName + "' is not supported" );
    }

  }




  /**
   * Use the first command line argument as the URI to the configuration file 
   * unless it is the encrypt keyword then the arguments are used to generate 
   * an encrypted string.
   * 
   * <p>If there are no arguments, then assume the configuration URI is stored 
   * in the {@code cfg.uri} system property.</p>
   * 
   * @param args The command line arguments passed to the main method
   */
  private static void parseArgs( String[] args ) {
    String cfgLoc = null;

    // Get the URI to our configuration from either the command line or the system properties
    if ( args != null && args.length > 0 ) {

      // if the first argument is "encrypt" perform an encryption operation 
      // using the rest of the command line arguments
      if ( ENCRYPT.equalsIgnoreCase( args[0] ) ) {
        encrypt( args );
        System.exit( 0 );
      } else {
        cfgLoc = args[0];
      }
    } else {
      cfgLoc = System.getProperties().getProperty( CFG_URI_PROPERTY );
    }

    // Parse the argument value into a URI
    if ( StringUtil.isNotBlank( cfgLoc ) ) {
      cfgUri = UriUtil.parse( cfgLoc );
    } else {
      System.err.println( LogMsg.createMsg( "Loader.error_no_config" ) );
      System.exit( 8 );
    }

  }




  /**
   * Generate a configuration from the file URI specified on the command line 
   * or the {@code cfg.uri} system property.
   * 
   * <p>If the URI has no scheme, then it is assimed to be a file name. If the 
   * file name is relative, the current directory will be checked for its 
   * existence. if it does not exist there, the {@code cfg.dir} system property 
   * is used to determine a common configuration directory and the existence of 
   * the file will be checked in that location. If the file does not exist 
   * there, a simple error message is displayed and the boot strap loader 
   * terminates.</p> 
   */
  private static void readConfig() {

    if ( cfgUri != null ) {
      if ( StringUtil.isBlank( cfgUri.getScheme() ) ) {
        File localfile = UriUtil.getFile( cfgUri );
        if ( localfile.exists() ) {
          cfgUri = FileUtil.getFileURI( localfile );
        } else {
          // see if ther is a system property with 
          String cfgDir = System.getProperties().getProperty( CFG_DIR_PROPERTY );

          if ( StringUtil.isNotBlank( cfgDir ) ) {
            File configDir = new File( cfgDir );
            if ( configDir.exists() ) {
              if ( configDir.isDirectory() ) {
                File cfgFile = new File( configDir, localfile.getPath() );
                if ( cfgFile.exists() ) {
                  // Success
                  cfgUri = FileUtil.getFileURI( cfgFile );
                } else {
                  System.out.println( "Could not load cfg from common directory: " + cfgFile.getAbsolutePath() );
                  System.exit( 9 );
                }
              } else {
                System.err.println( "CFG dir is not a directory" );
                System.exit( 10 );
              }

            } else {
              System.err.println( "CFG dir does not exist" );
              System.exit( 11 );
            }

          } else {

            // TODO: support loading from the class path to allow components to package utility configurations

            System.err.println( "CFG File not found" );
            System.exit( 12 );
          }
        } // localfile exists

      } // no scheme in uri implying a file

      try {
        configuration = Config.read( cfgUri );
      } catch ( IOException | ConfigurationException e ) {
        System.err.println( LogMsg.createMsg( "Loader.error_reading_configuration", cfgUri, e.getLocalizedMessage(), ExceptionUtil.stackTrace( e ) ) );
        System.exit( 7 );
      }
    } // cfguri !null
  }




  /**
   * Determine the loader to use from the given configuration and create an 
   * instance of it.
   * 
   * <p>Once created, the loader will be passed the configuration resulting in 
   * a configured loader</p>
   * 
   * @param args the command line arguments passed to this bootstrap loader
   *  
   * @return a configured loader or null if there was not "CLASS" attribute in 
   *         the root of the configuration indicating was not found.
   */
  private static Loader buildLoader( String[] args ) {
    //System.out.println(JSONMarshaler.toFormattedString( configuration ));
    Loader retval = null;

    // Look for the class to load
    for ( DataField field : configuration.getFields() ) {
      if ( ConfigTag.CLASS.equalsIgnoreCase( field.getName() ) ) {
        String className = field.getStringValue();
        if ( className != null && StringUtil.countOccurrencesOf( className, "." ) < 1 ) {
          className = BootStrap.class.getPackage().getName() + "." + className;
        }

        try {
          Class<?> clazz = Class.forName( className );
          Constructor<?> ctor = clazz.getConstructor();
          Object object = ctor.newInstance();

          if ( object instanceof Loader ) {
            retval = (Loader)object;
            try {
              retval.setCommandLineArguments( args );
              retval.configure( configuration );
            } catch ( ConfigurationException e ) {
              System.err.println( LogMsg.createMsg( "Loader.could_not_config_loader", object.getClass().getName(), e.getClass().getSimpleName(), e.getMessage() ) );
              System.exit( 6 );
            }
          } else {
            System.err.println( LogMsg.createMsg( "Loader.class_is_not_loader", className ) );
            System.exit( 5 );
          }
        } catch ( ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e ) {
          System.err.println( LogMsg.createMsg( "Loader.instantiation_error", className, e.getClass().getName(), e.getMessage() ) );
          System.exit( 4 );
        }
      }
    }

    return retval;
  }




  /**
   * Add a shutdown hook into the JVM to help us shut everything down nicely.
   * 
   * @param loader The loader to terminate
   */
  private static void registerShutdownHook( final Loader loader ) {
    try {
      Runtime.getRuntime().addShutdownHook( new Thread( "LoaderHook" ) {
        public void run() {
          Log.info( LogMsg.createMsg( "Loader.runtime_terminating", new Date() ) );

          if ( loader != null ) {
            loader.shutdown();
          }

          Log.info( LogMsg.createMsg( "Loader.runtime_terminated", new Date() ) );
        }
      } );
    } catch ( java.lang.NoSuchMethodError nsme ) {
      // Ignore
    } catch ( Throwable e ) {
      // Ignore
    }
  }




  /**
   * Use the first argument in the command line (or the 'cfg.uri' system 
   * property) to specify a URI of a configuration file to load.
   * 
   * @param args command line arguments.
   */
  public static void main( String[] args ) {

    // Parse the command line arguments
    parseArgs( args );

    if ( cfgUri != null ) {

      // Read in the configuration
      readConfig();

      // Create a loader from the configuration
      Loader loader = buildLoader( args );

      // If we have a loader
      if ( loader != null ) {

        // Register a shutdown method to terminate cleanly when the JVM exit
        registerShutdownHook( loader );

        // Start the loader running in the current thread
        try {
          loader.start();
        } catch ( Throwable t ) {
          System.err.println( LogMsg.createMsg( "Loader.logic_error_from_loader", t.getLocalizedMessage(), ExceptionUtil.stackTrace( t ) ) );
          System.exit( 3 );
        }
      } else {
        System.err.println( LogMsg.createMsg( "Loader.no_loader_configured" ) );
        System.exit( 2 );
      }
    } else {
      System.err.println( LogMsg.createMsg( "Loader.no_config_uri_defined" ) );
      System.exit( 1 );
    }

    // Normal termination
    System.exit( 0 );

  }

}
