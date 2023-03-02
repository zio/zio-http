package zio.http.middleware

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Trace, ZIO}

import zio.http._
import zio.http.middleware.Auth.Credentials
import zio.http.model.Headers.{BasicSchemeName, BearerSchemeName}
import zio.http.model.{HeaderNames, Headers, Status}

private[zio] trait Auth {

  /**
   * Creates a middleware for basic authentication
   */
  final def basicAuth(f: Credentials => Boolean): RequestHandlerMiddleware[Any, Nothing] =
    customAuth(
      _.basicAuthorizationCredentials match {
        case Some(credentials) => f(credentials)
        case None              => false
      },
      Headers(HeaderNames.wwwAuthenticate, BasicSchemeName),
    )

  /**
   * Creates a middleware for basic authentication that checks if the
   * credentials are same as the ones given
   */
  final def basicAuth(u: String, p: String): RequestHandlerMiddleware[Any, Nothing] =
    basicAuth { credentials => (credentials.uname == u) && (credentials.upassword == p) }

  /**
   * Creates a middleware for basic authentication using an effectful
   * verification function
   */
  final def basicAuthZIO[R, E](f: Credentials => ZIO[R, E, Boolean])(implicit
    trace: Trace,
  ): RequestHandlerMiddleware[R, E] =
    customAuthZIO(
      _.basicAuthorizationCredentials match {
        case Some(credentials) => f(credentials)
        case None              => ZIO.succeed(false)
      },
      Headers(HeaderNames.wwwAuthenticate, BasicSchemeName),
    )

  /**
   * Creates a middleware for bearer authentication that checks the token using
   * the given function
   * @param f:
   *   function that validates the token string inside the Bearer Header
   */
  final def bearerAuth(f: String => Boolean): RequestHandlerMiddleware[Any, Nothing] =
    customAuth(
      _.bearerToken match {
        case Some(token) => f(token)
        case None        => false
      },
      Headers(HeaderNames.wwwAuthenticate, BearerSchemeName),
    )

  /**
   * Creates a middleware for bearer authentication that checks the token using
   * the given effectful function
   * @param f:
   *   function that effectfully validates the token string inside the Bearer
   *   Header
   */
  final def bearerAuthZIO[R, E](
    f: String => ZIO[R, E, Boolean],
  )(implicit trace: Trace): RequestHandlerMiddleware[R, E] =
    customAuthZIO(
      _.bearerToken match {
        case Some(token) => f(token)
        case None        => ZIO.succeed(false)
      },
      Headers(HeaderNames.wwwAuthenticate, BearerSchemeName),
    )

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app.
   */
  final def customAuth(
    verify: Headers => Boolean,
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): RequestHandlerMiddleware[Any, Nothing] =
    new RequestHandlerMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionHandler[Request] { request =>
          if (verify(request.headers)) handler
          else Handler.status(responseStatus).addHeaders(responseHeaders)
        }
    }

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app using an effectful verification
   * function.
   */
  final def customAuthZIO[R, E](
    verify: Headers => ZIO[R, E, Boolean],
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): RequestHandlerMiddleware[R, E] =
    new RequestHandlerMiddleware[R, E] {
      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler
          .fromFunctionZIO[Request] { request =>
            verify(request.headers).map {
              case true  => handler
              case false => Handler.status(responseStatus).addHeaders(responseHeaders)
            }
          }
          .flatten
    }
}

object Auth {
  case class Credentials(uname: String, upassword: String)
}
