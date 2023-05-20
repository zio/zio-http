/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.internal.middlewares

import zio.{Cause, Tag, Trace, ZEnvironment, ZIO}

import zio.http._
import zio.http.internal.middlewares.Auth.Credentials

private[zio] trait Auth {

  /**
   * Creates a middleware for basic authentication
   */
  final def basicAuth(f: Credentials => Boolean): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    customAuth(
      _.header(Header.Authorization) match {
        case Some(Header.Authorization.Basic(userName, password)) =>
          f(Credentials(userName, password))
        case _                                                    => false
      },
      Headers(Header.WWWAuthenticate.Basic()),
    )

  /**
   * Creates a middleware for basic authentication that checks if the
   * credentials are same as the ones given
   */
  final def basicAuth(u: String, p: String): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    basicAuth { credentials => (credentials.uname == u) && (credentials.upassword == p) }

  /**
   * Creates a middleware for basic authentication using an effectful
   * verification function
   */
  final def basicAuthZIO[R, E](f: Credentials => ZIO[R, E, Boolean])(implicit
    trace: zio.http.Trace,
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    customAuthZIO(
      _.header(Header.Authorization) match {
        case Some(Header.Authorization.Basic(userName, password)) =>
          f(Credentials(userName, password))
        case _                                                    => ZIO.succeed(false)
      },
      Headers(Header.WWWAuthenticate.Basic()),
    )

  /**
   * Creates a middleware for bearer authentication that checks the token using
   * the given function
   * @param f:
   *   function that validates the token string inside the Bearer Header
   */
  final def bearerAuth(f: String => Boolean): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    customAuth(
      _.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) => f(token)
        case _                                        => false
      },
      Headers(Header.WWWAuthenticate.Bearer(realm = "Access")),
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
  )(implicit trace: zio.http.Trace): RequestHandlerMiddleware[Nothing, R, E, Any] =
    customAuthZIO(
      _.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) => f(token)
        case _                                        => ZIO.succeed(false)
      },
      Headers(Header.WWWAuthenticate.Bearer(realm = "Access")),
    )

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app.
   */
  final def customAuth(
    verify: Request => Boolean,
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    new RequestHandlerMiddleware.Simple[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: zio.http.Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionHandler[Request] { request =>
          if (verify(request)) handler
          else Handler.status(responseStatus).addHeaders(responseHeaders)
        }
    }

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app, and provides a context to the request
   * handlers.
   */
  final def customAuthProviding[R0, Context: Tag](
    provide: Request => Option[Context],
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): RequestHandlerMiddleware.WithOut[
    R0 with Context,
    Any,
    Nothing,
    Any,
    ({ type OutEnv[Env] = R0 })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
  ] =
    new RequestHandlerMiddleware.Contextual[R0 with Context, Any, Nothing, Any] { self =>
      type OutEnv[Env] = R0
      type OutErr[Err] = Err

      private def applyToHandler[R1 >: R0 with Context, Err1](
        handler: Handler[R1, Err1, Request, Response],
        context: Context,
      )(implicit trace: zio.http.Trace): Handler[R0, Err1, Request, Response] =
        handler.provideSomeEnvironment[R0](_.union[Context](ZEnvironment(context)))

      override def apply[R1 >: R0 with Context, Err1](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: zio.http.Trace): Handler[R0, Err1, Request, Response] =
        Handler.fromFunctionHandler[Request] { request =>
          provide(request) match {
            case Some(context) => applyToHandler(handler, context)
            case None          => Handler.status(responseStatus).addHeaders(responseHeaders)
          }
        }

      private def applyToHttp[R1 >: R0 with Context, Err1](
        http: Http[R1, Err1, Request, Response],
        context: Context,
      )(implicit trace: zio.http.Trace): Http[R0, Err1, Request, Response] =
        http.asInstanceOf[Http[_, _, _, _]] match {
          case Http.Empty(errorHandler)           =>
            Http.Empty(
              errorHandler
                .asInstanceOf[Option[Cause[Nothing] => ZIO[R1, Nothing, Response]]]
                .map(_.andThen(_.provideSomeEnvironment[R0](_.union[Context](ZEnvironment(context))))),
            )
          case Http.Static(handler, errorHandler) =>
            Http.Static(
              applyToHandler(handler.asInstanceOf[Handler[R1, Err1, Request, Response]], context),
              errorHandler
                .asInstanceOf[Option[Cause[Nothing] => ZIO[R1, Nothing, Response]]]
                .map(_.andThen(_.provideSomeEnvironment[R0](_.union[Context](ZEnvironment(context))))),
            )
          case route: Http.Route[_, _, _, _]      =>
            Http
              .fromHttpZIO[Request] { in =>
                route
                  .asInstanceOf[Http.Route[R1, Err1, Request, Response]]
                  .run(in)
                  .provideSomeEnvironment[R0](_.union[Context](ZEnvironment(context)))
                  .map { (http: Http[R1, Err1, Request, Response]) =>
                    applyToHttp(http, context)
                  }
              }
              .withErrorHandler(
                route.errorHandler
                  .asInstanceOf[Option[Cause[Nothing] => ZIO[R1, Nothing, Response]]]
                  .map(_.andThen(_.provideSomeEnvironment[R0](_.union[Context](ZEnvironment(context))))),
              )
        }

      override def apply[R1 >: R0 with Context, Err1](
        http: Http[R1, Err1, Request, Response],
      )(implicit trace: zio.http.Trace): Http[R0, Err1, Request, Response] =
        Http.fromHttpZIO[Request] { request =>
          ZIO.succeed(provide(request)).map {
            case Some(context) =>
              applyToHttp(http, context)
            case None          =>
              Handler.status(responseStatus).addHeaders(responseHeaders).toHttp
          }

        }
    }

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app, and provides a context to the request
   * handlers.
   */
  final def customAuthProvidingZIO[R0, R, E, Context: Tag](
    provide: Request => ZIO[R, E, Option[Context]],
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): RequestHandlerMiddleware.WithOut[
    R0 with R with Context,
    R,
    E,
    Any,
    ({ type OutEnv[Env] = R0 with R })#OutEnv,
    ({ type OutErr[Err] = Err })#OutErr,
  ] =
    new RequestHandlerMiddleware.Contextual[R0 with R with Context, R, E, Any] { self =>
      type OutEnv[Env] = R0 with R
      type OutErr[Err] = Err

      private def applyToHandler[R1 >: R0 with R with Context <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
        context: Context,
      )(implicit trace: zio.http.Trace): ZIO[Any, Nothing, Handler[R0 with R, Err1, Request, Response]] =
        ZIO.succeed(handler.provideSomeEnvironment[R0 with R](_.union[Context](ZEnvironment(context))))

      override def apply[R1 >: R0 with R with Context <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: zio.http.Trace): Handler[R0 with R, Err1, Request, Response] =
        Handler
          .fromFunctionZIO[Request] { request =>
            provide(request).flatMap {
              case Some(context) =>
                applyToHandler(handler, context)
              case None          =>
                ZIO.succeed(Handler.status(responseStatus).addHeaders(responseHeaders))
            }
          }
          .flatten

      private def applyToHttp[R1 >: R0 with R with Context <: R, Err1 >: E](
        http: Http[R1, Err1, Request, Response],
        context: Context,
      )(implicit trace: zio.http.Trace): Http[R0 with R, Err1, Request, Response] =
        http.asInstanceOf[Http[_, _, _, _]] match {
          case Http.Empty(errorHandler)           =>
            Http.Empty(
              errorHandler
                .asInstanceOf[Option[Cause[Nothing] => ZIO[R1, Nothing, Response]]]
                .map(_.andThen(_.provideSomeEnvironment[R0 with R](_.union[Context](ZEnvironment(context))))),
            )
          case Http.Static(handler, errorHandler) =>
            Http.Static(
              Handler
                .fromZIO(
                  applyToHandler(handler.asInstanceOf[Handler[R1, Err1, Request, Response]], context),
                )
                .flatten,
              errorHandler
                .asInstanceOf[Option[Cause[Nothing] => ZIO[R1, Nothing, Response]]]
                .map(_.andThen(_.provideSomeEnvironment[R0 with R](_.union[Context](ZEnvironment(context))))),
            )
          case route: Http.Route[_, _, _, _]      =>
            Http
              .fromHttpZIO[Request] { in =>
                route
                  .asInstanceOf[Http.Route[R1, Err1, Request, Response]]
                  .run(in)
                  .provideSomeEnvironment[R0 with R](_.union[Context](ZEnvironment(context)))
                  .map { (http: Http[R1, Err1, Request, Response]) =>
                    applyToHttp(http, context)
                  }
              }
              .withErrorHandler(
                route.errorHandler
                  .asInstanceOf[Option[Cause[Nothing] => ZIO[R1, Nothing, Response]]]
                  .map(_.andThen(_.provideSomeEnvironment[R0 with R](_.union[Context](ZEnvironment(context))))),
              )
        }

      override def apply[R1 >: R0 with R with Context <: R, Err1 >: E](
        http: Http[R1, Err1, Request, Response],
      )(implicit trace: zio.http.Trace): Http[R0 with R, Err1, Request, Response] =
        Http.fromHttpZIO[Request] { request =>
          provide(request).map {
            case Some(context) =>
              applyToHttp(http, context)
            case None          =>
              Handler.status(responseStatus).addHeaders(responseHeaders).toHttp
          }

        }
    }

  /**
   * Creates an authentication middleware that only allows authenticated
   * requests to be passed on to the app using an effectful verification
   * function.
   */
  final def customAuthZIO[R, E](
    verify: Request => ZIO[R, E, Boolean],
    responseHeaders: Headers = Headers.empty,
    responseStatus: Status = Status.Unauthorized,
  ): RequestHandlerMiddleware[Nothing, R, E, Any] =
    new RequestHandlerMiddleware.Simple[R, E] {
      override def apply[R1 <: R, Err1 >: E](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: zio.http.Trace): Handler[R1, Err1, Request, Response] =
        Handler
          .fromFunctionZIO[Request] { request =>
            verify(request).map {
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
