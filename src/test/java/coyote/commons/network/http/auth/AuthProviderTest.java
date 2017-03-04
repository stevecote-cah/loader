/*
 * Copyright (c) 2017 Stephan D. Cote' - All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the MIT License which accompanies this distribution, and is 
 * available at http://creativecommons.org/licenses/MIT/
 *
 * Contributors:
 *   Stephan D. Cote 
 *      - Initial concept and initial implementation
 */
package coyote.commons.network.http.auth;

//import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import coyote.commons.NetUtil;
import coyote.commons.network.http.HTTPD;
import coyote.commons.network.http.TestHttpClient;
import coyote.commons.network.http.TestResponse;
import coyote.commons.network.http.TestRouter;
import coyote.commons.network.http.nugget.HTTPDRouter;


/**
 * 
 */
public class AuthProviderTest {

  private static HTTPDRouter server = null;
  private static int port = 62611;
  private static final TestAuthProvider AUTH_PROVIDER = new TestAuthProvider();




  /**
   * @throws java.lang.Exception
   */
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    port = NetUtil.getNextAvailablePort( port );
    server = new TestRouter( port );

    // set a test auth provider in the base server
    HTTPDRouter.setAuthProvider( AUTH_PROVIDER );

    // add a protected uri resource 
    server.addRoute( "/", Integer.MAX_VALUE, ProtectedHandler.class );

    try {
      server.start( HTTPD.SOCKET_READ_TIMEOUT, true );
    } catch ( IOException ioe ) {
      System.err.println( "Couldn't start server:\n" + ioe );
      System.exit( -1 );
    }
  }




  /**
   * @throws java.lang.Exception
   */
  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    server.stop();
  }




  @Test
  public void test() {
    AUTH_PROVIDER.allowAllConnections();
    AUTH_PROVIDER.allowAllAuthentications();
    AUTH_PROVIDER.allowAllAuthorizations();
    TestResponse response = TestHttpClient.sendGet( "http://localhost:" + port );
    assertTrue( response.isComplete() );
    assertEquals( response.getStatus(), 200 );

    // Make sure the server drops the connection if SSL is not enabled.
    // No status should be returned so the response should be incomplete.
    AUTH_PROVIDER.rejectAllConnections();
    response = TestHttpClient.sendPost( "http://localhost:" + port );
    assertFalse( response.isComplete() );

  }

}
