package zio.http

import zio._

trait Middleware[-Env, +CtxOut2] {
  def apply[Env1 <: Env, CtxIn, CtxOut](
    stack: MiddlewareStack[Env1, CtxIn, CtxOut],
  ): MiddlewareStack[Env1, CtxIn, CtxOut with CtxOut2]
}
object Middleware extends zio.http.internal.HeaderModifier[Middleware[Any, Any]] {

  override def updateHeaders(update: Headers => Headers): Middleware[Any, Any] =
    updateResponse(_.updateHeaders(update))

  def updateResponse(update: Response => Response): Middleware[Any, Any] =
    new Middleware[Any, Any] {
      def apply[Env1 <: Any, CtxIn, CtxOut](
        stack: MiddlewareStack[Env1, CtxIn, CtxOut],
      ): MiddlewareStack[Env1, CtxIn, CtxOut] =
        stack.++[Env1, Any](
          MiddlewareStack.outgoing[Env1](
            Handler.fromFunction[Either[Response, Response]] { either =>
              either.map(update)
            },
          ),
        )
    }

  val identity: Middleware[Any, Any] = new Middleware[Any, Any] {
    def apply[Env1 <: Any, CtxIn, CtxOut](
      stack: MiddlewareStack[Env1, CtxIn, CtxOut],
    ): MiddlewareStack[Env1, CtxIn, CtxOut] =
      stack
  }
}
