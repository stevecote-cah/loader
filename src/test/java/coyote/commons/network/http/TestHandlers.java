package coyote.commons.network.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import coyote.commons.network.http.auth.AuthProvider;
import coyote.commons.network.http.handler.Error404UriHandler;
import coyote.commons.network.http.handler.GeneralHandler;
import coyote.commons.network.http.handler.HTTPDRouter;
import coyote.commons.network.http.handler.NotImplementedHandler;
import coyote.commons.network.http.handler.StaticPageHandler;
import coyote.commons.network.http.handler.UriResource;


public class TestHandlers {

  private static PipedOutputStream stdIn;

  private static Thread serverStartThread;




  public static void main( final String[] args ) {
    {
      final String uri = "def";
      Pattern.compile( "([A-Za-z0-9\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=]+)" );
      final Pattern URI_PATTERN = Pattern.compile( "([A-Za-z0-9\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=]+)" );
      System.out.println( URI_PATTERN.matcher( uri ).matches() );
    }

    final String uri = "photos/abc/def";
    final Pattern URI_PATTERN = Pattern.compile( "photos/([A-Za-z0-9\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=]+)/([A-Za-z0-9\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=]+)" );
    final Matcher matcher = URI_PATTERN.matcher( uri );
    System.out.println( "--------------->" + "/" + uri );
    while ( matcher.matches() ) {
      System.out.println( matcher.group() );
    }
    for ( int index = 0; index < matcher.groupCount(); index++ ) {
      System.out.println( matcher.group() );
    }
  }




  @BeforeClass
  public static void setUp() throws Exception {
    stdIn = new PipedOutputStream();
    System.setIn( new PipedInputStream( stdIn ) );
    serverStartThread = new Thread( new Runnable() {

      @Override
      public void run() {
        final String[] args = {};
        TestRouter.main( args );
      }
    } );
    serverStartThread.start();
    // give the server some tine to start.
    Thread.sleep( 200 );
  }




  @AfterClass
  public static void tearDown() throws Exception {
    stdIn.write( "\n\n".getBytes() );
    serverStartThread.join( 2000 );
    assertFalse( serverStartThread.isAlive() );
  }




  @Test(expected = ClassCastException.class)
  public void checkIniParameter1() throws Exception {
    new UriResource( "browse", 100, null, (AuthProvider)null, "init" ).initParameter( String.class );
    new UriResource( "browse", 100, null, (AuthProvider)null, "init" ).initParameter( Integer.class );
  }




  @Test
  public void checkIniParameter2() throws Exception {
    assertEquals( "init", new UriResource( "browse", 100, null, (AuthProvider)null, "init" ).initParameter( String.class ) );
    assertNull( new UriResource( "browse", 100, null, (AuthProvider)null ).initParameter( String.class ) );
  }




  @Test
  public void doDeletedRoute() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpGet httpget = new HttpGet( "http://localhost:9090/toBeDeleted" );
    CloseableHttpResponse response = httpclient.execute( httpget );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body><h3>Error 404</h3><p>The requested resource does not exist.</p></body></html>", string );
    response.close();
  }




  @Test
  public void doEncodedRequest() throws ClientProtocolException, IOException {
    CloseableHttpClient httpclient = HttpClients.createDefault();
    HttpGet httpget = new HttpGet( "http://localhost:9090/general/param%201/param%202" );
    CloseableHttpResponse response = httpclient.execute( httpget );
    assertEquals( Status.OK.getRequestStatus(), response.getStatusLine().getStatusCode() );
  }




  @Test
  public void doExceptionRequest() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpGet httpget = new HttpGet( "http://localhost:9090/interface" );
    CloseableHttpResponse response = httpclient.execute( httpget );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "Error: java.lang.InstantiationException : coyote.commons.network.http.handler.UriResponder", string );
    response.close();
  }




  @Test
  public void doGeneralParams() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpGet httpget = new HttpGet( "http://localhost:9090/general/value1/value2?param3=value3&param4=value4" );

    CloseableHttpResponse response = httpclient.execute( httpget );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body><h1>Url: /general/value1/value2</h1><br><p>Param 'param3' = value3</p><p>Param 'param4' = value4</p>", string );
    response.close();
  }




  @Test
  public void doIndexHandler() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpGet httpget = new HttpGet( "http://localhost:9090/index.html" );
    CloseableHttpResponse response = httpclient.execute( httpget );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body></body></html>", string );
    response.close();
  }




  @Test
  public void doMissingHandler() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpGet httpget = new HttpGet( "http://localhost:9090/photos/abc/def" );
    CloseableHttpResponse response = httpclient.execute( httpget );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body><h3>Not implemented</h3><p>The uri is mapped in the router, but no handler is specified.</p></body></html>", string );
    response.close();
  }




  @Test
  public void doNonRouterRequest() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpGet httpget = new HttpGet( "http://localhost:9090/test" );
    CloseableHttpResponse response = httpclient.execute( httpget );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "Return: java.lang.String.toString() -> ", string );
    response.close();
  }




  @Test
  public void doOtherMethod() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpTrace httphead = new HttpTrace( "http://localhost:9090/index.html" );
    CloseableHttpResponse response = httpclient.execute( httphead );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body></body></html>", string );
    response.close();
  }




  @Test
  public void doSomeBasicMethodTest() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpGet httpget = new HttpGet( "http://localhost:9090/user/content" );
    CloseableHttpResponse response = httpclient.execute( httpget );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body>User handler. Method: GET<br><h1>Uri parameters:</h1><div> Param: id&nbsp;Value: content</div><h1>Query parameters:</h1></body></html>", string );
    response.close();

    HttpPost httppost = new HttpPost( "http://localhost:9090/user/content" );
    response = httpclient.execute( httppost );
    entity = response.getEntity();
    string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body>User handler. Method: POST<br><h1>Uri parameters:</h1><div> Param: id&nbsp;Value: content</div><h1>Query parameters:</h1></body></html>", string );
    response.close();

    HttpPut httpgput = new HttpPut( "http://localhost:9090/user/content" );
    response = httpclient.execute( httpgput );
    entity = response.getEntity();
    string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body>User handler. Method: PUT<br><h1>Uri parameters:</h1><div> Param: id&nbsp;Value: content</div><h1>Query parameters:</h1></body></html>", string );
    response.close();

    HttpDelete httpdelete = new HttpDelete( "http://localhost:9090/user/content" );
    response = httpclient.execute( httpdelete );
    entity = response.getEntity();
    string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body>User handler. Method: DELETE<br><h1>Uri parameters:</h1><div> Param: id&nbsp;Value: content</div><h1>Query parameters:</h1></body></html>", string );
    response.close();
  }




  @Test
  public void doStreamOfData() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpGet httpget = new HttpGet( "http://localhost:9090/stream" );
    CloseableHttpResponse response = httpclient.execute( httpget );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "a stream of data ;-)", string );
    response.close();
  }




  @Test
  public void doUriSelection1() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpGet httpget = new HttpGet( "http://localhost:9090/user/help" );
    CloseableHttpResponse response = httpclient.execute( httpget );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body><h1>Url: /user/help</h1><br><p>no params in url</p><br>", string );
    response.close();
  }




  @Test(expected = IllegalStateException.class)
  public void illegalMethod1() throws Exception {
    new TestRouter.UserHandler().getData();
  }




  @Test(expected = IllegalStateException.class)
  public void illegalMethod2() throws Exception {
    new GeneralHandler().getText();
  }




  @Test(expected = IllegalStateException.class)
  public void illegalMethod3() throws Exception {
    new StaticPageHandler().getText();
  }




  @Test(expected = IllegalStateException.class)
  public void illegalMethod4() throws Exception {
    new StaticPageHandler().getMimeType();
  }




  @Test
  public void normalize() throws Exception {
    assertNull( HTTPDRouter.normalizeUri( null ) );
    assertEquals( "", HTTPDRouter.normalizeUri( "/" ) );
    assertEquals( "xxx/yyy", HTTPDRouter.normalizeUri( "/xxx/yyy" ) );
    assertEquals( "xxx/yyy", HTTPDRouter.normalizeUri( "/xxx/yyy/" ) );
  }




  private byte[] readContents( HttpEntity entity ) throws IOException {
    InputStream instream = entity.getContent();
    return readContents( instream );
  }




  private byte[] readContents( final InputStream instream ) throws IOException {
    byte[] bytes;
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    try {
      final byte[] buffer = new byte[1024];
      int count;
      while ( ( count = instream.read( buffer ) ) >= 0 ) {
        out.write( buffer, 0, count );
      }
      bytes = out.toByteArray();
    }
    finally {
      instream.close();
    }
    return bytes;
  }




  @Test
  public void staticFiles() throws Exception {
    CloseableHttpClient httpclient = HttpClients.createDefault();

    HttpTrace httphead = new HttpTrace( "http://localhost:9090/browse/content.html" );
    CloseableHttpResponse response = httpclient.execute( httphead );
    HttpEntity entity = response.getEntity();
    String string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body><h3>A page of content</h3></body></html>", string );
    response.close();

    httphead = new HttpTrace( "http://localhost:9090/browse/dir/content.html" );
    response = httpclient.execute( httphead );
    entity = response.getEntity();
    string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body><h3>Another content page</h3></body></html>", string );
    response.close();

    httphead = new HttpTrace( "http://localhost:9090/browse/dir/icon.png" );
    response = httpclient.execute( httphead );
    entity = response.getEntity();
    assertEquals( "image/png", entity.getContentType().getValue() );
    response.close();

    httphead = new HttpTrace( "http://localhost:9090/browse/dir/xxx.html" );
    response = httpclient.execute( httphead );
    entity = response.getEntity();
    string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body><h3>Error 404</h3><p>The requested resource does not exist.</p></body></html>", string );
    response.close();

    httphead = new HttpTrace( "http://localhost:9090/browse/dir/" );
    response = httpclient.execute( httphead );
    entity = response.getEntity();
    string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "<html><body><h3>This is an index page</h3></body></html>", string );
    response.close();

    httphead = new HttpTrace( "http://localhost:9090/browse/exception.html" );
    response = httpclient.execute( httphead );
    assertEquals( Status.REQUEST_TIMEOUT.getRequestStatus(), response.getStatusLine().getStatusCode() );
    entity = response.getEntity();
    string = new String( readContents( entity ), "UTF-8" );
    assertEquals( "", string );
    response.close();
  }




  @Test
  public void testError404UriHandlerGetMimeType() {
    assertEquals( "Error404UriHandler mime type should be text/html", "text/html", new Error404UriHandler().getMimeType() );
  }




  @Test
  public void testError404UriHandlerGetStatus() {
    assertEquals( "Error404UriHandler#getStatus should return NOT_FOUND status", Status.NOT_FOUND, new Error404UriHandler().getStatus() );
  }




  @Test
  public void testGeneralHandlerGetStatus() {
    assertEquals( "GeneralHandler#getStatus should return OK status", Status.OK, new GeneralHandler().getStatus() );
  }




  @Test
  public void testNotImplementedHandlerGetMimeType() {
    assertEquals( "NotImplementedHandler mime type should be text/html", "text/html", new NotImplementedHandler().getMimeType() );
  }




  @Test
  public void testNotImplementedHandlerGetStatus() {
    assertEquals( "NotImplementedHandler#getStatus should return OK status", Status.OK, new NotImplementedHandler().getStatus() );
  }




  @Test
  public void testStaticPageHandlerGetStatus() {
    assertEquals( "StaticPageHandler#getStatus should return OK status", Status.OK, new StaticPageHandler().getStatus() );
  }




  @Test
  public void testUriResourceMatch() {
    final UriResource resource = new UriResource( "browse", 100, null, null, "init" );
    assertNull( "UriResource should not match incorrect URL, and thus, should not return a URI parameter map", resource.match( "/xyz/pqr/" ) );
    assertNotNull( "UriResource should match the correct URL, and thus, should return a URI parameter map", resource.match( "browse" ) );
  }




  @Test
  public void uriToString() throws Exception {
    assertEquals( //
        "UriResource{uri='photos/:customer_id/:photo_id', urlParts=[customer_id, photo_id]}", //
        new UriResource( "/photos/:customer_id/:photo_id", 100, GeneralHandler.class, (AuthProvider)null ).toString() );
  }

}