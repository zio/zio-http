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
  def ++[Env1 <: Env, CtxOut3](that: Middleware[Env1, CtxOut3]): Middleware[Env1, CtxOut2 with CtxOut3] =
    new Middleware[Env1, CtxOut2 with CtxOut3] {
      def apply[Env2 <: Env1, CtxIn, CtxOut](
        stack: MiddlewareStack[Env2, CtxIn, CtxOut],
      ): MiddlewareStack[Env2, CtxIn, CtxOut with CtxOut2 with CtxOut3] =
        that.apply[Env2, CtxIn, CtxOut with CtxOut2](self.apply[Env2, CtxIn, CtxOut](stack))
    }
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
  def interceptPatchZIO[R, S](fromRequest: Request => ZIO[R, Response, S]): InterceptPatchZIO[R, S] =
    new InterceptPatchZIO[R, S](fromRequest)

  /**
   * Creates a middleware that produces a Patch for the Response
   */
  def patch(f: Response => Response.Patch): Middleware[Any, Any] =
    interceptPatch(_ => ())((response, _) => f(response))

  /**
   * Creates a middleware that produces a Patch for the Response effectfully.
   */
  def patchZIO[R](f: Response => ZIO[R, Response, Response.Patch]): Middleware[R, Any] =
    interceptPatchZIO[R, Unit](_ => ZIO.unit)((response, _) => f(response))

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
