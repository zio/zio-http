package zhttp.http.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.Headers.BasicSchemeName
import zhttp.http._

trait AuthMiddlewares {

  /**
   * Creates an authentication middleware that only allows authenticated requests to be passed on to the app.
   */
  def CustomAuth(verify: Headers => Boolean, responseHeaders: Headers = Headers.empty): HttpMiddleware[Any, Nothing] =
    Middleware.ifThenElse[Request](req => verify(req.getHeaders))(
      _ => Middleware.identity,
      _ => Middleware.fromHttp(Http.status(Status.FORBIDDEN).addHeaders(responseHeaders)),
    )

  /**
   * Creates a middleware for basic authentication
   */
  def basicAuth(f: Header => Boolean): HttpMiddleware[Any, Nothing] =
    CustomAuth(
      _.getBasicAuthorizationCredentials match {
        case Some(header) => f(header)
        case None         => false
      },
      Headers(HttpHeaderNames.WWW_AUTHENTICATE, BasicSchemeName),
    )

  /**
   * Creates a middleware for basic authentication that checks if the credentials are same as the ones given
   */
  def basicAuth(u: String, p: String): HttpMiddleware[Any, Nothing] =
    basicAuth { case (user, password) => (user == u) && (password == p) }
}
