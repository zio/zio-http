package zio.http

import zio._

final case class Routes2[-Env, +Err](routes: Chunk[zio.http.Route[Env, Err]]) { self =>
  final def ++[Env1 <: Env, Err1 >: Err](that: Routes2[Env1, Err1]): Routes2[Env1, Err1] =
    Routes2(self.routes ++ that.routes)

  def lookup(request: Request): Option[Handler[Env, Response, Request, Response]] = ???

  def toHttpApp[Env1 <: Env, Err1 >: Err](implicit trace: Trace): HttpApp2[Env1] =
    HttpApp2(
      Handler.fromFunctionZIO { request =>
        lookup(request) match {
          case Some(handler) => handler(request)
          case None          => ZIO.succeed(Response(Status.NotFound))
        }
      },
      (cause: Cause[Any]) =>
        ZIO.logDebugCause(s"Unhandled error in HttpApp ($trace)", cause).as(Response(Status.InternalServerError)),
    )
}
object Routes2                                                                {
  def apply[Env, Err](route: zio.http.Route[Env, Err], routes: zio.http.Route[Env, Err]*): Routes2[Env, Err] =
    Routes2(Chunk(route) ++ Chunk.fromIterable(routes))
}
