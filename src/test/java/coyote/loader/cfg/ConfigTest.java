package coyote.loader.cfg;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

//import static org.junit.Assert.*;
import org.junit.Test;

import coyote.commons.GUID;
import coyote.dataframe.marshal.JSONMarshaler;


public class ConfigTest {

  @Test
  public void test() {
    Config config = new Config();
    config.setName( "Loader" );
    config.setClassName( coyote.loader.DefaultLoader.class.getName() );
    config.setId( GUID.randomGUID().toString() );

    // The configuration section of all the components this loader is to load
    Config componentCfg = new Config();

    // Create a configuration for component 1
    Config cfg = new Config();
    cfg.setName( "Component1" );
    cfg.setClassName( coyote.loader.SimpleComponent.class.getName() );
    cfg.setId( "F56A" );
    componentCfg.add( cfg );

    cfg = new Config();
    cfg.setName( "Component2" );
    cfg.setClassName( coyote.loader.SimpleComponent.class.getName() );
    cfg.setId( "DB46" );
    componentCfg.add( cfg );

    cfg = new Config();
    cfg.setName( "Component3" );
    cfg.setClassName( coyote.loader.SimpleComponent.class.getName() );
    cfg.setId( "8A0A" );
    componentCfg.add( cfg );

    // Add the component configuration to the main Loader config
    config.add( "Components", componentCfg );

    // This is a standard configuration for a loader
    System.out.println( JSONMarshaler.toFormattedString( config ) );

  }




  @Test
  public void testGetInt() {

    Config cfg = new Config();
    cfg.set( "port", "123" );

    try {
      int value = cfg.getInt( "PORT" );
      assertTrue( value == 123 );
    } catch ( NumberFormatException e ) {
      fail( "Could not retrieve as an integer" );
    }

    cfg.set( "fail", null );
    try {
      int value = cfg.getInt( "FAIL" );
      fail( "Should have thrown an exception" );

      value = cfg.getInt( "NotThere" );
      fail( "Should have thrown an exception - null value" );
    } catch ( NumberFormatException e ) {}

    try {
      int value = cfg.getInt( "NotThere" );
      fail( "Should have thrown an exception - not found" );
    } catch ( NumberFormatException e ) {}

  }
}
