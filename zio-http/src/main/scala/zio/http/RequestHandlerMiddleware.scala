package zio.http

import zio.Trace

trait RequestHandlerMiddleware[-R, +Err]
    extends HandlerMiddleware[Nothing, R, Err, Any, Request, Response, Request, Response] {
  override type OutEnv[Env0] = R
  override type OutErr[Err0] = Err
}

object RequestHandlerMiddleware {

  // TODO: if RequestHandlerMiddleware is not a type alias we need these duplicates
  def identity: RequestHandlerMiddleware[Any, Nothing] =
    new RequestHandlerMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] = handler
    }
}
