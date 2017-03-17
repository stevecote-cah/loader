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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import coyote.commons.StringUtil;
import coyote.commons.network.MimeType;
import coyote.commons.network.http.HTTPD;
import coyote.commons.network.http.IHTTPSession;
import coyote.commons.network.http.Response;
import coyote.commons.network.http.SecurityResponseException;
import coyote.commons.network.http.Status;
import coyote.commons.network.http.auth.Auth;
import coyote.commons.network.http.auth.AuthProvider;
import coyote.loader.log.Log;


/**
 * 
 */
public class UriResource {

  private static final Pattern PARAM_PATTERN = Pattern.compile( "(?<=(^|/)):[a-zA-Z0-9_-]+(?=(/|$))" );

  private static final String PARAM_MATCHER = "([A-Za-z0-9\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=\\s]+)";

  // default empty parameter map
  private static final Map<String, String> EMPTY = Collections.unmodifiableMap( new HashMap<String, String>() );

  private final String uri;

  private final Pattern uriPattern;

  final int priority;

  // The class to use handling the URI
  private final Class<?> handler;

  // the initialization parameters for the handler
  private final Object[] initParameter;

  private final List<String> uriParams = new ArrayList<String>();

  private final AuthProvider authProvider;




  /**
   * Create a URI Resource
   * 
   * @param uri the 
   * @param priority
   * @param handler
   * @param authProvider
   * @param initParameter
   */
  public UriResource( final String uri, final int priority, final Class<?> handler, final AuthProvider authProvider, final Object... initParameter ) {
    this.handler = handler;
    this.authProvider = authProvider;
    this.initParameter = initParameter;
    if ( uri != null ) {
      this.uri = HTTPDRouter.normalizeUri( uri );
      uriPattern = createUriPattern();
    } else {
      uriPattern = null;
      this.uri = null;
    }

    // prioritize this resource based on the number of parameters; the fewer 
    // the parameters, the higher the priority
    this.priority = priority + ( uriParams.size() * 1000 );
  }




  private Pattern createUriPattern() {
    String patternUri = uri;
    Matcher matcher = PARAM_PATTERN.matcher( patternUri );
    int start = 0;
    while ( matcher.find( start ) ) {
      uriParams.add( patternUri.substring( matcher.start() + 1, matcher.end() ) );
      patternUri = new StringBuilder( patternUri.substring( 0, matcher.start() ) )//
          .append( PARAM_MATCHER )//
          .append( patternUri.substring( matcher.end() ) ).toString();
      start = matcher.start() + PARAM_MATCHER.length();
      matcher = PARAM_PATTERN.matcher( patternUri );
    }
    return Pattern.compile( patternUri );
  }




  public String getUri() {
    return uri;
  }




  /**
   * @return the number of initialization parameters set in the resource.
   */
  public int getInitParameterLength() {
    return initParameter.length;
  }




  /**
   * Cast the first initialization parameter to the given class.
   * 
   * @param paramClazz the class to perform the cast
   * 
   * @return the first parameter as an object of the given class
   * 
   * @throws ClassCastException if the cast fails
   */
  public <T> T initParameter( final Class<T> paramClazz ) {
    return initParameter( 0, paramClazz );
  }




  /**
   * Cast the initialization parameter at the given index to the given class.
   * 
   * @param parameterIndex the 0-based index of the parameter to retrieve and cast
   * @param paramClazz the class to perform the cast
   * 
   * @return the given parameter as an object of the given class
   * 
   * @throws ClassCastException if the cast fails
   */
  public <T> T initParameter( final int parameterIndex, final Class<T> paramClazz ) {
    if ( initParameter.length > parameterIndex ) {
      return paramClazz.cast( initParameter[parameterIndex] );
    }
    Log.append( HTTPD.EVENT, "ERROR: init parameter index not available " + parameterIndex );
    return null;
  }




  /**
   * See if the URL matches this resources RegEx pattern, if it does, return 
   * the parameters parsed from this URI based on the routing pattern.
   *  
   * @param url the URL to match
   * 
   * @return parameters pulled from the URL based on this resource's matching 
   *     pattern (may be empty) or null if the URL did not match at all.
   */
  public Map<String, String> match( final String url ) {
    final Matcher matcher = uriPattern.matcher( url );
    if ( matcher.matches() ) {
      if ( uriParams.size() > 0 ) {
        final Map<String, String> result = new HashMap<String, String>();
        for ( int i = 1; i <= matcher.groupCount(); i++ ) {
          result.put( uriParams.get( i - 1 ), matcher.group( i ) );
        }
        return result;
      } else {
        return EMPTY;
      }
    }
    return null;
  }




  public Response process( final Map<String, String> urlParams, final IHTTPSession session ) throws SecurityResponseException {
    String error = "Error: Problems while processing URI resource";
    if ( handler != null ) {
      try {
        final Object object = handler.newInstance();

        // If this is a URI Responder, have it process the request
        if ( object instanceof UriResponder ) {
          final UriResponder responder = (UriResponder)object;

          // determine which method to call
          Class[] params = { UriResource.class, Map.class, IHTTPSession.class };
          Method method = null;
          switch ( session.getMethod() ) {
            case GET:
              method = handler.getMethod( "get", params );
              break;
            case POST:
              method = handler.getMethod( "post", params );
              break;
            case PUT:
              method = handler.getMethod( "put", params );
              break;
            case DELETE:
              method = handler.getMethod( "delete", params );
              break;
            default:
              method = handler.getMethod( "other", String.class, UriResource.class, Map.class, IHTTPSession.class );
          }

          // Check for method level authentication and authorization annotation
          if ( method.isAnnotationPresent( Auth.class ) ) {
            Annotation annotation = method.getAnnotation( Auth.class );
            Auth auth = (Auth)annotation;
            if ( authProvider != null ) {
              if ( auth.requireSSL() && !authProvider.isSecureConnection( session ) ) {
                // RFC and OWASP differ in their recommendations. I prefer OWASP's version - don't respond to the request and just drop the packet.
                throw new SecurityResponseException( "Resource requires secure connection" );
              }
              if ( !authProvider.isAuthenticated( session ) ) {
                if ( auth.required() ) {
                  return Response.createFixedLengthResponse( Status.UNAUTHORIZED, MimeType.TEXT.getType(), "Authentication Required" );
                }
              }
              if ( StringUtil.isNotBlank( auth.groups() ) && !authProvider.isAuthorized( session, auth.groups() ) ) {
                return Response.createFixedLengthResponse( Status.FORBIDDEN, MimeType.TEXT.getType(), "Not Authorized" );
              }
            } else {
              // should never happen, but who knows?
              Log.append( HTTPD.EVENT, "ERROR: No Authentication Handler Set: while processing for '" + session.getUri() + "' from " + session.getRemoteIpAddress() + ":" + session.getRemoteIpPort() );
              Log.error( "No Authentication Handler Set in Server: check HTTP log for more details" );
              return Response.createFixedLengthResponse( Status.INTERNAL_ERROR, MimeType.TEXT.getType(), "Server Error" );
            }
          }

          // All auth checks have passed, invoke processing
          switch ( session.getMethod() ) {
            case GET:
            case POST:
            case PUT:
            case DELETE:
              return (Response)method.invoke( responder, this, urlParams, session );
            default:
              return (Response)method.invoke( responder, session.getMethod().toString(), this, urlParams, session );
          }
        } else {
          // This is some other object...display it generically
          return Response.createFixedLengthResponse( Status.OK, MimeType.TEXT.getType(), //
              new StringBuilder( "Return: " ) //
                  .append( handler.getCanonicalName() ) //
                  .append( ".toString() -> " ) //
                  .append( object ) //
                  .toString() );
        }
      } catch ( final Exception e ) {
        error = "Error: " + e.getClass().getName() + " : " + e.getMessage();
        Log.append( HTTPD.EVENT, error, e );
        if ( e instanceof SecurityResponseException ) {
          throw (SecurityResponseException)e;
        }
      }
    }
    return Response.createFixedLengthResponse( Status.INTERNAL_ERROR, MimeType.TEXT.getType(), error );
  }




  @Override
  public String toString() {
    StringBuilder b = new StringBuilder( "UriResource{uri='" );
    b.append( ( uri == null ? "/" : uri ) );
    b.append( "', urlParts=" );
    b.append( uriParams );
    b.append( '}' );
    return b.toString();
  }

}