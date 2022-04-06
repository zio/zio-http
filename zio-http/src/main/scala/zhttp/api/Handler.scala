package zhttp.api

import zhttp.http.HttpApp
import zio.ZIO

final case class Handler[-R, +E, Params, Input, Output](
  api: API[Params, Input, Output],
  handle: ((Params, Input)) => ZIO[R, E, Output],
) { self =>

  def ++[R1 <: R, E1 >: E](that: Handler[R1, E1, _, _, _]): Handlers[R1, E1] =
    Handlers(this) ++ that

  def toHttp: HttpApp[R, E] =
    ServerInterpreter.handlerToHttpApp(self)

}

object Handler {

  def make[R, E, Params, Input, Output](
    api: API[Params, Input, Output],
  )(
    handle: ((Params, Input)) => ZIO[R, E, Output],
  ): Handler[R, E, Params, Input, Output] =
    Handler(api, handle)
}
