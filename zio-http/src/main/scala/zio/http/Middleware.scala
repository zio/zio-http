package zio.http

import zio._

trait Middleware[-Env, +CtxOut2] { self =>
  def apply[Env1 <: Env, CtxIn, CtxOut](
    stack: MiddlewareStack[Env1, CtxIn, CtxOut],
  ): MiddlewareStack[Env1, CtxIn, CtxOut with CtxOut2]

  /**
   * Sequential composition of middleware. Based on the composition semantics of
   * protocol stacks.
   */
  final def ++[Env1 <: Env, CtxOut3](that: Middleware[Env1, CtxOut3]): Middleware[Env1, CtxOut2 with CtxOut3] =
    new Middleware[Env1, CtxOut2 with CtxOut3] {
      def apply[Env2 <: Env1, CtxIn, CtxOut](
        stack: MiddlewareStack[Env2, CtxIn, CtxOut],
      ): MiddlewareStack[Env2, CtxIn, CtxOut with CtxOut2 with CtxOut3] =
        that.apply[Env2, CtxIn, CtxOut with CtxOut2](self.apply[Env2, CtxIn, CtxOut](stack))
    }

  final def whenHeader(condition: Headers => Boolean): Middleware[Env, Any] =
    Middleware.whenHeader(condition)(self)

  final def whenRequest[Env1 <: Env](condition: Request => Boolean): Middleware[Env1, Any] =
    Middleware.whenRequest(condition)(self)

  final def whenRequestZIO[Env1 <: Env](
    condition: Request => ZIO[Env1, Either[Response, Response], Boolean],
  ): Middleware[Env1, Any] =
    Middleware.whenRequestZIO(condition)(self)

  final def whenResponse[Env1 <: Env](condition: Response => Boolean): Middleware[Env1, Any] =
    Middleware.whenResponse(condition)(self)

  final def whenResponseZIO[Env1 <: Env](
    condition: Response => ZIO[Env1, Response, Boolean],
  ): Middleware[Env1, Any] =
    Middleware.whenResponseZIO(condition)(self)

  final def whenStatus[Env1 <: Env](condition: Status => Boolean): Middleware[Env1, Any] =
    Middleware.whenStatus(condition)(self)
}
object Middleware extends zio.http.internal.HeaderModifier[Middleware[Any, Any]] {
  def fail(response: Response): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply[Env1 <: Any, CtxIn, CtxOut](
        stack: MiddlewareStack[Env1, CtxIn, CtxOut],
      ): MiddlewareStack[Env1, CtxIn, CtxOut] =
        stack.++[Env1, Any](MiddlewareStack.fail(response))
    }

  def failWith(f: Request => Response): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply[Env1 <: Any, CtxIn, CtxOut](
        stack: MiddlewareStack[Env1, CtxIn, CtxOut],
      ): MiddlewareStack[Env1, CtxIn, CtxOut] =
        stack.++[Env1, Any](MiddlewareStack.failWith(f))
    }

  val identity: Middleware[Any, Any] = new Middleware[Any, Any] {
    def apply[Env1 <: Any, CtxIn, CtxOut](
      stack: MiddlewareStack[Env1, CtxIn, CtxOut],
    ): MiddlewareStack[Env1, CtxIn, CtxOut] =
      stack
  }

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
  ): Middleware[Env, CtxOut] =
    new Middleware[Env, CtxOut] {
      def apply[Env1 <: Env, CtxIn, CtxOut2](
        stack: MiddlewareStack[Env1, CtxIn, CtxOut2],
      ): MiddlewareStack[Env1, CtxIn, CtxOut with CtxOut2] = {
        type IncomingIn = (Request, ZEnvironment[CtxIn])

        val ifTrue2  = ifTrue(stack).protocol
        val ifFalse2 = ifFalse(stack).protocol

        MiddlewareStack(ProtocolStack.cond[IncomingIn](tuple => predicate(tuple._1))(ifTrue2, ifFalse2))
      }
    }

  def ifRequestThenElseZIO[Env, CtxOut](
    predicate: Request => ZIO[Env, Either[Response, Response], Boolean],
  )(
    ifTrue: Middleware[Env, CtxOut],
    ifFalse: Middleware[Env, CtxOut],
  ): Middleware[Env, CtxOut] =
    new Middleware[Env, CtxOut] {
      def apply[Env1 <: Env, CtxIn, CtxOut2](
        stack: MiddlewareStack[Env1, CtxIn, CtxOut2],
      ): MiddlewareStack[Env1, CtxIn, CtxOut with CtxOut2] = {
        type IncomingIn = (Request, ZEnvironment[CtxIn])

        val ifTrue2  = ifTrue(stack).protocol
        val ifFalse2 = ifFalse(stack).protocol

        MiddlewareStack(ProtocolStack.condZIO[IncomingIn](tuple => predicate(tuple._1))(ifTrue2, ifFalse2))
      }
    }

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
  def patch(f: Response => Response.Patch): Middleware[Any, Any] =
    interceptPatch(_ => ())((response, _) => f(response))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  def patchZIO[Env](f: Response => ZIO[Env, Response, Response.Patch]): Middleware[Env, Any] =
    interceptPatchZIO[Env, Unit](_ => ZIO.unit)((response, _) => f(response))

  def redirect(url: URL, isPermanent: Boolean): Middleware[Any, Any] =
    fail(Response.redirect(url, isPermanent))

  def redirectTrailingSlash(
    isPermanent: Boolean,
  ): Middleware[Any, Any] =
    ifRequestThenElse(request => request.url.path.hasTrailingSlash && request.url.queryParams.isEmpty)(
      ifTrue = Middleware.identity,
      ifFalse = updatePath(_.dropTrailingSlash) ++ failWith(request => Response.redirect(request.url, isPermanent)),
    )

  def runAfter[Env](effect: ZIO[Env, Nothing, Any])(implicit trace: Trace): Middleware[Env, Any] =
    updateResponseZIO(response => effect.as(response))

  def runBefore[Env](effect: ZIO[Env, Nothing, Any])(implicit trace: Trace): Middleware[Env, Any] =
    updateRequestZIO(request => effect.as(request))

  /**
   * Creates a middleware for signing cookies
   */
  def signCookies(secret: String): Middleware[Any, Any] =
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

  def status(status: Status): Middleware[Any, Any] =
    patch(_ => Response.Patch.status(status))

  override def updateHeaders(update: Headers => Headers): Middleware[Any, Any] =
    updateResponse(_.updateHeaders(update))

  def updateMethod(update: Method => Method): Middleware[Any, Any] =
    updateRequest(request => request.copy(method = update(request.method)))

  def updatePath(update: Path => Path): Middleware[Any, Any] =
    updateRequest(request => request.copy(url = request.url.copy(path = update(request.url.path))))

  def updateRequest(update: Request => Request): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply[Env1 <: Any, CtxIn, CtxOut](
        stack: MiddlewareStack[Env1, CtxIn, CtxOut],
      ): MiddlewareStack[Env1, CtxIn, CtxOut] =
        stack.++[Env1, Any](
          MiddlewareStack.interceptIncomingHandler {
            Handler.fromFunction[(Request, ZEnvironment[Any])] { case (request, env) =>
              (update(request), env)
            }
          },
        )
    }

  def updateRequestZIO[Env](update: Request => ZIO[Env, Response, Request]): Middleware[Env, Any] =
    new Middleware[Env, Any] {
      def apply[Env1 <: Env, CtxIn, CtxOut](
        stack: MiddlewareStack[Env1, CtxIn, CtxOut],
      ): MiddlewareStack[Env1, CtxIn, CtxOut] =
        stack.++[Env1, Any](
          MiddlewareStack.interceptIncomingHandler {
            Handler.fromFunctionZIO[(Request, ZEnvironment[Any])] { case (request, env) =>
              update(request).map((_, env)).mapError(Left(_))
            }
          },
        )
    }

  def updateResponse(update: Response => Response): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply[Env1 <: Any, CtxIn, CtxOut](
        stack: MiddlewareStack[Env1, CtxIn, CtxOut],
      ): MiddlewareStack[Env1, CtxIn, CtxOut] =
        stack.++[Env1, Any](
          MiddlewareStack.interceptOutgoingHandler[Env1](
            Handler.fromFunction[Either[Response, Response]] { either =>
              either.map(update)
            },
          ),
        )
    }

  def updateResponseZIO[Env](update: Response => ZIO[Env, Response, Response]): Middleware[Env, Any] =
    new Middleware[Env, Any] {
      def apply[Env1 <: Env, CtxIn, CtxOut](
        stack: MiddlewareStack[Env1, CtxIn, CtxOut],
      ): MiddlewareStack[Env1, CtxIn, CtxOut] =
        stack.++[Env1, Any](
          MiddlewareStack.interceptOutgoingHandler[Env1](
            Handler.fromFunctionZIO[Either[Response, Response]] { either =>
              either match {
                case Right(response) => update(response).either
                case Left(failure)   => Exit.succeed(Left(failure))
              }
            },
          ),
        )
    }

  def updateURL(update: URL => URL): Middleware[Any, Any] =
    updateRequest(request => request.copy(url = update(request.url)))

  def whenHeader[Env](condition: Headers => Boolean)(
    middleware: Middleware[Env, Any],
  ): Middleware[Env, Any] =
    ifHeaderThenElse(condition)(ifFalse = identity, ifTrue = middleware)

  /**
   * Applies the middleware only if status matches the condition
   */
  def whenStatus[Env](condition: Status => Boolean)(
    middleware: Middleware[Env, Any],
  ): Middleware[Env, Any] =
    whenResponse(response => condition(response.status))(middleware)

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def whenResponse[Env](condition: Response => Boolean)(
    middleware: Middleware[Env, Any],
  ): Middleware[Env, Any] =
    ???

  /**
   * Applies the middleware only if the condition function effectfully evaluates
   * to true
   */
  def whenResponseZIO[Env](condition: Response => ZIO[Env, Response, Boolean])(
    middleware: Middleware[Env, Any],
  ): Middleware[Env, Any] =
    ???

  /**
   * Applies the middleware only if the condition function evaluates to true
   */
  def whenRequest[Env](condition: Request => Boolean)(
    middleware: Middleware[Env, Any],
  ): Middleware[Env, Any] =
    ifRequestThenElse(condition)(ifFalse = identity, ifTrue = middleware)

  def whenRequestZIO[Env](condition: Request => ZIO[Env, Either[Response, Response], Boolean])(
    middleware: Middleware[Env, Any],
  ): Middleware[Env, Any] =
    ifRequestThenElseZIO(condition)(ifFalse = identity, ifTrue = middleware)

  final class InterceptPatch[State](val fromRequest: Request => State) extends AnyVal {
    def apply(result: (Response, State) => Response.Patch): Middleware[Any, Any] =
      new Middleware[Any, Any] {
        def apply[Env1 <: Any, CtxIn, CtxOut](
          stack: MiddlewareStack[Env1, CtxIn, CtxOut],
        ): MiddlewareStack[Env1, CtxIn, CtxOut] =
          stack.++[Env1, Any](
            MiddlewareStack.interceptHandlerStateful(
              Handler.fromFunction[(Request, ZEnvironment[Any])] { case (request, env) =>
                val state = fromRequest(request)
                (state, (request, env))
              },
            )(
              Handler.fromFunction[(State, Either[Response, Response])] { case (state, either) =>
                either match {
                  case Right(response) => Right(result(response, state)(response))
                  case Left(failure)   => Left(failure)
                }
              },
            ),
          )
      }
  }

  final class InterceptPatchZIO[Env, State](val fromRequest: Request => ZIO[Env, Response, State]) extends AnyVal {
    def apply(result: (Response, State) => ZIO[Env, Response, Response.Patch]): Middleware[Env, Any] =
      new Middleware[Env, Any] {
        def apply[Env1 <: Env, CtxIn, CtxOut](
          stack: MiddlewareStack[Env1, CtxIn, CtxOut],
        ): MiddlewareStack[Env1, CtxIn, CtxOut] =
          stack.++[Env1, Any](
            MiddlewareStack.interceptHandlerStateful(
              Handler.fromFunctionZIO[(Request, ZEnvironment[Any])] { case (request, env) =>
                fromRequest(request).map((_, (request, env))).mapError(Left(_))
              },
            )(
              Handler.fromFunctionZIO[(State, Either[Response, Response])] { case (state, either) =>
                either match {
                  case Right(response) => result(response, state).map(patch => patch(response)).either
                  case Left(failure)   => Exit.succeed(Left(failure))
                }
              },
            ),
          )
      }
  }
}
