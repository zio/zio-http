package zhttp.api

import zhttp.http.{Headers, HttpApp, Status}
import zio.ZIO

final case class ApiResponse[A](value: A, headers: Headers = Headers.empty, status: Status = Status.Ok)

final case class Handler[-R, +E, Params, Input, Output](
  api: API[Params, Input, Output],
  handle: ((Params, Input)) => ZIO[R, E, ApiResponse[Output]],
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
    handle: ((Params, Input)) => ZIO[R, E, ApiResponse[Output]],
  ): Handler[R, E, Params, Input, Output] =
    Handler(api, handle)
}
