/*
 * Copyright (c) 2004 Stephan D. Cote' - All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the MIT License which accompanies this distribution, and is 
 * available at http://creativecommons.org/licenses/MIT/
 *
 * Contributors:
 *   Stephan D. Cote 
 */
package coyote.commons.network.http.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import coyote.commons.network.http.HTTPD;
import coyote.commons.network.http.IHTTPSession;
import coyote.commons.network.http.Response;
import coyote.commons.network.http.SecurityResponseException;
import coyote.commons.network.http.auth.AuthProvider;
import coyote.loader.log.Log;


/**
 * This is the heart of the URI routing mechanism in a routing HTTP server.
 */
public class UriRouter {

  private final List<UriResource> mappings;

  private UriResource error404Url;

  private Class<?> notImplemented;




  /**
   * Default constructor
   */
  public UriRouter() {
    mappings = new ArrayList<UriResource>();
  }




  /**
   * Add a route to this router with the handler class for that route.
   * 
   * @param url the regex to match against the request URL
   * @param priority the priority in which the router will check the route, 
   *        lower values return before larger priorities.
   * @param handler the handler class for this mapping. If null, the 
   *        NotImplemented handler will be used.
   * @param authProvider the auth provider the URI resource should use for 
   *        this route
   * @param initParameter the initialization parameters for the handler when 
   *        it receives a request.
   */
  void addRoute( final String url, final int priority, final Class<?> handler, final AuthProvider authProvider, final Object... initParameter ) {
    if ( url != null ) {
      if ( handler != null ) {
        mappings.add( new UriResource( url, priority + mappings.size(), handler, authProvider, initParameter ) );
      } else {
        mappings.add( new UriResource( url, priority + mappings.size(), notImplemented, authProvider ) );
      }
      sortMappings();
    }
  }




  /**
   * Search in the mappings if the given request URI matches some of the rules.
   * 
   * <p>If there are more than one match, this returns the rule with least 
   * parameters. For example: mapping 1 = /user/:id  - mapping 2 = /user/help. 
   * If the incoming URI is www.example.com/user/help - mapping 2 is returned. 
   * If the incoming URI is www.example.com/user/3232 - mapping 1 is 
   * returned.</p>
   * 
   * @param session the HTTP session encapsulating the request
   * 
   * @return the Response from the URI resource processing
   * 
   * @throws SecurityResponseException if processing request generated a security exception
   */
  public Response process( final IHTTPSession session ) throws SecurityResponseException {

    final String request = HTTPDRouter.normalizeUri( session.getUri() );

    Map<String, String> params = null;
    UriResource uriResource = error404Url;

    // For all the resources, see which one matches first
    for ( final UriResource resource : mappings ) {
      params = resource.match( request );
      if ( params != null ) {
        uriResource = resource;
        break;
      }
    }

    if ( Log.isLogging( HTTPD.EVENT ) ) {
      if ( error404Url == uriResource ) {
        Log.append( HTTPD.EVENT, "No handler defined for '" + request + "' from " + session.getRemoteIpAddress() + ":" + session.getRemoteIpPort() );
      } else {
        Log.append( HTTPD.EVENT, "Handler '" + uriResource + "' servicing '" + session.getMethod() + "' request for '" + request + "' from " + session.getRemoteIpAddress() + ":" + session.getRemoteIpPort() );
      }
    }
    // Have the found (or default 404) URI resource process the session
    return uriResource.process( params, session );
  }




  void removeRoute( final String url ) {
    final String uriToDelete = HTTPDRouter.normalizeUri( url );
    final Iterator<UriResource> iter = mappings.iterator();
    while ( iter.hasNext() ) {
      final UriResource uriResource = iter.next();
      if ( uriToDelete.equals( uriResource.getUri() ) ) {
        iter.remove();
        break;
      }
    }
  }




  public void setNotFoundHandler( final Class<?> handler ) {
    error404Url = new UriResource( null, 100, handler, null );
  }




  public void setNotImplemented( final Class<?> handler ) {
    notImplemented = handler;
  }




  /**
   * @return the list of URI resource objects responsible for handling 
   *         requests of the server.
   */
  public List<UriResource> getMappings() {
    return mappings;
  }




  private void sortMappings() {
    Collections.sort( mappings, new Comparator<UriResource>() {

      @Override
      public int compare( final UriResource o1, final UriResource o2 ) {
        return o1.priority - o2.priority;
      }
    } );
  }

}