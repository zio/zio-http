/*
 * Copyright 2023 the ZIO HTTP contributors.
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

package zio.http

import zio._

/**
 * A [[zio.http.Middleware]] is a kind of [[zio.http.ProtocolStack]] that is
 * specialized to transform a handler's incoming requests and outgoing
 * responses. Each layer in the stack corresponds to a separate transformation.
 *
 * Layers may incorporate layer-specific information into a generic type
 * parameter, referred to as middleware context, which composes using tupling.
 *
 * Layers may also be stateful at the level of each transformation application.
 * So, for example, a layer that is timing request durations may capture the
 * start time of the request in the incoming interceptor, and pass this state to
 * the outgoing interceptor, which can then compute the duration.
 *
 * The [[zio.http.Middleware]] is more than just a wrapper around
 * [[zio.http.ProtocolStack]], as its concatenation operator has been
 * specialized to entuple contexts, so that each layer may only add context to
 * the contextual output.
 */
final case class Middleware[-Env, +CtxOut](
  protocol: ProtocolStack[
    Env,
    Request,
    (Request, CtxOut),
    Response,
    Response,
  ],
) { self =>
  def ++[Env1 <: Env, CtxOut2](
    that: Middleware[Env1, CtxOut2],
  )(implicit zippable: Zippable[CtxOut, CtxOut2]): Middleware[Env1, zippable.Out] =
    Middleware {
      val combiner: ProtocolStack[Env1, (Request, CtxOut), (Request, zippable.Out), Response, Response] =
        ProtocolStack.interceptHandlerStateful[
          Env1,
          that.protocol.State,
          (Request, CtxOut),
          (Request, zippable.Out),
          Response,
          Response,
        ](
          Handler.fromFunctionZIO[(Request, CtxOut)] { tuple =>
            that.protocol.incoming(tuple._1).map { case (state, (request, env)) =>
              (state, (request, zippable.zip(tuple._2, env)))
            }
          },
        )(
          Handler.fromFunctionZIO[(that.protocol.State, Response)] { case (state, either) =>
            that.protocol.outgoing(state, either)
          },
        )

      self.protocol ++ combiner
    }

  def as[CtxOut2](ctxOut2: => CtxOut2): Middleware[Env, CtxOut2] =
    map(_ => ctxOut2)

  def map[CtxOut2](f: CtxOut => CtxOut2): Middleware[Env, CtxOut2] =
    Middleware(protocol.mapIncoming { case (request, ctx) => (request, f(ctx)) })

  def unit: Middleware[Env, Unit] = as(())

  def whenHeader(condition: Headers => Boolean): Middleware[Env, Unit] =
    Middleware.whenHeader(condition)(self.unit)

  def whenRequest(condition: Request => Boolean): Middleware[Env, Unit] =
    Middleware.whenRequest(condition)(self.unit)

  def whenRequestZIO[Env1 <: Env](condition: Request => ZIO[Env1, Response, Boolean]): Middleware[Env1, Unit] =
    Middleware.whenRequestZIO(condition)(self.unit)
}
object Middleware extends zio.http.internal.HeaderModifier[Middleware[Any, Unit]] {
  def fail(
    response: Response,
  ): Middleware[Any, Unit] =
    Middleware(ProtocolStack.interceptHandler(Handler.fail(response))(Handler.identity))

  def failWith(f: Request => Response): Middleware[Any, Unit] =
    Middleware(
      ProtocolStack.interceptHandler(Handler.fromFunctionExit[Request](in => Exit.fail(f(in))))(Handler.identity),
    )

  val identity: Middleware[Any, Unit] =
    interceptHandler[Any, Unit](Handler.identity[Request].map(_ -> ()))(Handler.identity)

  def ifHeaderThenElse[Env, Ctx](
    condition: Headers => Boolean,
  )(
    ifTrue: Middleware[Env, Ctx],
    ifFalse: Middleware[Env, Ctx],
  ): Middleware[Env, Ctx] =
    ifRequestThenElse(request => condition(request.headers))(ifTrue, ifFalse)

  def ifMethodThenElse[Env, Ctx](
    condition: Method => Boolean,
  )(
    ifTrue: Middleware[Env, Ctx],
    ifFalse: Middleware[Env, Ctx],
  ): Middleware[Env, Ctx] =
    ifRequestThenElse(request => condition(request.method))(ifTrue, ifFalse)

  def ifRequestThenElse[Env, CtxOut](
    predicate: Request => Boolean,
  )(
    ifTrue: Middleware[Env, CtxOut],
    ifFalse: Middleware[Env, CtxOut],
  ): Middleware[Env, CtxOut] = {
    val ifTrue2  = ifTrue.protocol
    val ifFalse2 = ifFalse.protocol

    Middleware(ProtocolStack.cond[Request](req => predicate(req))(ifTrue2, ifFalse2))
  }

  def ifRequestThenElseZIO[Env, CtxOut](
    predicate: Request => ZIO[Env, Response, Boolean],
  )(
    ifTrue: Middleware[Env, CtxOut],
    ifFalse: Middleware[Env, CtxOut],
  ): Middleware[Env, CtxOut] = {
    val ifTrue2  = ifTrue.protocol
    val ifFalse2 = ifFalse.protocol

    Middleware(ProtocolStack.condZIO[Request](req => predicate(req))(ifTrue2, ifFalse2))
  }

  def interceptHandler[Env, CtxOut](
    incoming0: Handler[Env, Response, Request, (Request, CtxOut)],
  )(
    outgoing0: Handler[Env, Nothing, Response, Response],
  ): Middleware[Env, CtxOut] =
    Middleware[Env, CtxOut](ProtocolStack.interceptHandler(incoming0)(outgoing0))

  def interceptHandlerStateful[Env, State0, CtxOut](
    incoming0: Handler[
      Env,
      Response,
      Request,
      (State0, (Request, CtxOut)),
    ],
  )(
    outgoing0: Handler[Env, Nothing, (State0, Response), Response],
  ): Middleware[Env, CtxOut] =
    Middleware[Env, CtxOut](ProtocolStack.interceptHandlerStateful(incoming0)(outgoing0))

  def interceptIncomingHandler[Env, CtxOut](
    handler: Handler[Env, Response, Request, (Request, CtxOut)],
  ): Middleware[Env, CtxOut] =
    interceptHandler(handler)(Handler.identity)

  def interceptOutgoingHandler[Env](
    handler: Handler[Env, Nothing, Response, Response],
  ): Middleware[Env, Unit] =
    interceptHandler[Env, Unit](Handler.identity[Request].map(_ -> ()))(handler)

  /**
   * Creates a new middleware using transformation functions
   */
  def interceptPatch[S](fromRequest: Request => S): InterceptPatch[S] = new InterceptPatch[S](fromRequest)

  /**
   * Creates a new middleware using effectful transformation functions
   */
  def interceptPatchZIO[Env, S](fromRequest: Request => ZIO[Env, Response, S]): InterceptPatchZIO[Env, S] =
    new InterceptPatchZIO[Env, S](fromRequest)

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  def patch(f: Response => Response.Patch): Middleware[Any, Unit] =
    interceptPatch(_ => ())((response, _) => f(response))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  def patchZIO[Env](f: Response => ZIO[Env, Response, Response.Patch]): Middleware[Env, Unit] =
    interceptPatchZIO[Env, Unit](_ => ZIO.unit)((response, _) => f(response))

  def redirect(url: URL, isPermanent: Boolean): Middleware[Any, Unit] =
    fail(Response.redirect(url, isPermanent))

  def redirectTrailingSlash(
    isPermanent: Boolean,
  ): Middleware[Any, Unit] =
    ifRequestThenElse(request => request.url.path.hasTrailingSlash && request.url.queryParams.isEmpty)(
      ifTrue = Middleware.identity,
      ifFalse = updatePath(_.dropTrailingSlash) ++ failWith(request => Response.redirect(request.url, isPermanent)),
    )

  def runAfter[Env](effect: ZIO[Env, Nothing, Any])(implicit trace: Trace): Middleware[Env, Unit] =
    updateResponseZIO(response => effect.as(response))

  def runBefore[Env](effect: ZIO[Env, Nothing, Any])(implicit trace: Trace): Middleware[Env, Unit] =
    updateRequestZIO(request => effect.as(request))

  /**
   * Creates a middleware for signing cookies
   */
  def signCookies(secret: String): Middleware[Any, Unit] =
    updateHeaders { headers =>
      headers.modify {
        case Header.SetCookie(cookie)                                                      =>
          Header.SetCookie(cookie.sign(secret))
        case header @ Header.Custom(name, value) if name.toString == Header.SetCookie.name =>
          Header.SetCookie.parse(value.toString) match {
            case Left(_)               => header
            case Right(responseCookie) => Header.SetCookie(responseCookie.value.sign(secret))
          }
        case header: Header                                                                => header
      }
    }

  def status(status: Status): Middleware[Any, Unit] =
    patch(_ => Response.Patch.status(status))

  override def updateHeaders(update: Headers => Headers): Middleware[Any, Unit] =
    updateResponse(_.updateHeaders(update))

  def updateMethod(update: Method => Method): Middleware[Any, Unit] =
    updateRequest(request => request.copy(method = update(request.method)))

  def updatePath(update: Path => Path): Middleware[Any, Unit] =
    updateRequest(request => request.copy(url = request.url.copy(path = update(request.url.path))))

  def updateRequest(update: Request => Request): Middleware[Any, Unit] =
    Middleware.interceptIncomingHandler {
      Handler.fromFunction[Request] { request =>
        (update(request), ())
      }
    }

  def updateRequestZIO[Env](update: Request => ZIO[Env, Response, Request]): Middleware[Env, Unit] =
    Middleware.interceptIncomingHandler {
      Handler.fromFunctionZIO[Request] { request =>
        update(request).map((_, ()))
      }
    }

  def updateResponse(update: Response => Response): Middleware[Any, Unit] =
    Middleware.interceptOutgoingHandler(Handler.fromFunction(update))

  def updateResponseZIO[Env](update: Response => ZIO[Env, Nothing, Response]): Middleware[Env, Unit] =
    Middleware.interceptOutgoingHandler(Handler.fromFunctionZIO[Response](update))

  def updateURL(update: URL => URL): Middleware[Any, Unit] =
    updateRequest(request => request.copy(url = update(request.url)))

  def whenHeader[Env](condition: Headers => Boolean)(
    middleware: Middleware[Env, Unit],
  ): Middleware[Env, Unit] =
    ifHeaderThenElse(condition)(ifFalse = identity, ifTrue = middleware)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def whenResponse(condition: Response => Boolean)(
    f: Response => Response,
  ): Middleware[Any, Unit] =
    Middleware(
      ProtocolStack.interceptHandler(Handler.identity[Request].map(_ -> ()))(Handler.fromFunctionZIO[Response] {
        response =>
          if (condition(response)) ZIO.succeed(f(response)) else ZIO.succeed(response)
      }),
    )

  /**
   * Applies the middleware only if the condition function effectfully evaluates
   * to true
   */
  def whenResponseZIO[Env](condition: Response => ZIO[Env, Response, Boolean])(
    f: Response => ZIO[Env, Response, Response],
  ): Middleware[Env, Unit] =
    Middleware(
      ProtocolStack.interceptHandler[Env, Request, (Request, Unit), Response, Response](
        Handler.identity[Request].map(_ -> ()),
      )(Handler.fromFunctionZIO[Response] { response =>
        condition(response)
          .flatMap[Env, Response, Response](bool => if (bool) f(response) else ZIO.succeed(response))
          .merge
      }),
    )

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def whenRequest[Env](condition: Request => Boolean)(
    middleware: Middleware[Env, Unit],
  ): Middleware[Env, Unit] =
    ifRequestThenElse(condition)(ifFalse = identity, ifTrue = middleware)

  def whenRequestZIO[Env](condition: Request => ZIO[Env, Response, Boolean])(
    middleware: Middleware[Env, Unit],
  ): Middleware[Env, Unit] =
    ifRequestThenElseZIO(condition)(ifFalse = identity, ifTrue = middleware)

  final class InterceptPatch[State](val fromRequest: Request => State) extends AnyVal {
    def apply(result: (Response, State) => Response.Patch): Middleware[Any, Unit] =
      Middleware.interceptHandlerStateful(
        Handler.fromFunction[Request] { request =>
          val state = fromRequest(request)
          (state, (request, ()))
        },
      )(
        Handler.fromFunction[(State, Response)] { case (state, response) => response.patch(result(response, state)) },
      )
  }

  final class InterceptPatchZIO[Env, State](val fromRequest: Request => ZIO[Env, Response, State]) extends AnyVal {
    def apply(result: (Response, State) => ZIO[Env, Response, Response.Patch]): Middleware[Env, Unit] =
      Middleware.interceptHandlerStateful(
        Handler.fromFunctionZIO[Request] { request =>
          fromRequest(request).map((_, (request, ())))
        },
      )(
        Handler.fromFunctionZIO[(State, Response)] { case (state, response) =>
          result(response, state).map(response.patch(_)).merge
        },
      )
  }
}
