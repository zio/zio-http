package zio.http.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import zio.http._
import zio.http.middleware.Auth.Credentials
import zio.http.model.Headers.{BasicSchemeName, BearerSchemeName}
import zio.http.model.{Headers, Status}
import zio.{Trace, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] trait Auth {

  /**
   * Creates a middleware for basic authentication
   */
  final def basicAuth(f: Credentials => Boolean)(implicit trace: Trace): HttpMiddleware[Any, Nothing] =
    basicAuthZIO(credentials => ZIO.succeed(f(credentials)))

  /**
   * Creates a middleware for basic authentication that checks if the
   * credentials are same as the ones given
   */
  final def basicAuth(u: String, p: String)(implicit trace: Trace): HttpMiddleware[Any, Nothing] =
    basicAuth { case credentials => (credentials.uname == u) && (credentials.upassword == p) }

  /**
   * Creates a middleware for basic authentication using an effectful
   * verification function
   */
  final def basicAuthZIO[R, E](f: Credentials => ZIO[R, E, Boolean])(implicit trace: Trace): HttpMiddleware[R, E] =
    customAuthZIO(
      _.basicAuthorizationCredentials match {
        case Some(credentials) => f(credentials)
        case None              => ZIO.succeed(false)
      },
      Headers(HttpHeaderNames.WWW_AUTHENTICATE, BasicSchemeName),
    )

  /**
   * Creates a middleware for bearer authentication that checks the token using
   * the given function
   * @param f:
   *   function that validates the token string inside the Bearer Header
   */
  final def bearerAuth(f: String => Boolean)(implicit trace: Trace): HttpMiddleware[Any, Nothing] =
    bearerAuthZIO(token => ZIO.succeed(f(token)))

  /**
   * Creates a middleware for bearer authentication that checks the token using
   * the given effectful function
   * @param f:
   *   function that effectfully validates the token string inside the Bearer
   *   Header
   */
  final def bearerAuthZIO[R, E](f: String => ZIO[R, E, Boolean])(implicit trace: Trace): HttpMiddleware[R, E] =
    customAuthZIO(
      _.bearerToken match {
        case Some(token) => f(token)
        case None        => ZIO.succeed(false)
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
    responseStatus: Status = Status.Unauthorized,
  )(implicit trace: Trace): HttpMiddleware[Any, Nothing] =
    customAuthZIO(headers => ZIO.succeed(verify(headers)), responseHeaders, responseStatus)

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app using an effectful verification
   * function.
   */
  final def customAuthZIO[R, E](
    verify: Headers => ZIO[R, E, Boolean],
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  )(implicit trace: Trace): HttpMiddleware[R, E] =
    Middleware.ifThenElseZIO[Request](req => verify(req.headers))(
      _ => Middleware.identity,
      _ => Middleware.fromHttp(Http.status(responseStatus).addHeaders(responseHeaders)),
    )
}

object Auth {
  case class Credentials(uname: String, upassword: String)
}
