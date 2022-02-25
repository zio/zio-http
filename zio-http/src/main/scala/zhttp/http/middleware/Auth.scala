package zhttp.http.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.Headers.{BasicSchemeName, BearerSchemeName}
import zhttp.http._
import zhttp.http.middleware.Auth.Credentials
import zio.{UIO, ZIO}

private[zhttp] trait Auth {

  /**
   * Creates a middleware for basic authentication
   */
  final def basicAuth(f: Credentials => Boolean): HttpMiddleware[Any, Nothing] =
    basicAuthZIO(credentials => UIO(f(credentials)))

  /**
   * Creates a middleware for basic authentication using an effectful
   * verification function
   */
  final def basicAuthZIO[R, E](f: Credentials => ZIO[R, E, Boolean]): HttpMiddleware[R, E] =
    customAuthZIO(
      _.basicAuthorizationCredentials match {
        case Some(credentials) => f(credentials)
        case None              => UIO(false)
      },
      Headers(HttpHeaderNames.WWW_AUTHENTICATE, BasicSchemeName),
    )

  /**
   * Creates a middleware for basic authentication that checks if the
   * credentials are same as the ones given
   */
  final def basicAuth(u: String, p: String): HttpMiddleware[Any, Nothing] =
    basicAuth { case credentials => (credentials.uname == u) && (credentials.upassword == p) }

  final def jwtAuth(f: String => Boolean): HttpMiddleware[Any, Nothing] =
    jwtAuthZIO(token => UIO(f(token)))

  final def jwtAuthZIO[R, E](f: String => ZIO[R, E, Boolean]): HttpMiddleware[R, E] =
    customAuthZIO(
      _.bearerToken match {
        case Some(token) => f(token)
        case None        => UIO(false)
      },
      Headers(HttpHeaderNames.WWW_AUTHENTICATE, BearerSchemeName),
    )

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app.
   */
  final def customAuth(
    verify: Headers => Boolean,
    responseHeaders: Headers = Headers.empty,
  ): HttpMiddleware[Any, Nothing] =
    customAuthZIO(headers => UIO(verify(headers)), responseHeaders)

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app using an effectful verification
   * function.
   */
  final def customAuthZIO[R, E](
    verify: Headers => ZIO[R, E, Boolean],
    responseHeaders: Headers = Headers.empty,
  ): HttpMiddleware[R, E] =
    Middleware.ifThenElseZIO[Request](req => verify(req.headers))(
      _ => Middleware.identity,
      _ => Middleware.fromHttp(Http.status(Status.FORBIDDEN).addHeaders(responseHeaders)),
    )
}

object Auth {
  case class Credentials(uname: String, upassword: String)
}
