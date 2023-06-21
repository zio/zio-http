package zio.http

import zio._

final case class Routes2[-Env](routes: Chunk[zio.http.Route[Env]]) { self =>
  final def ++[Env1 <: Env](that: Routes2[Env1]): Routes2[Env1] =
    Routes2(self.routes ++ that.routes)

  def lookup(request: Request): Option[Handler[Env, Response, Request, Response]] = ???

  def toHttpApp[Env1 <: Env](implicit trace: Trace): HttpApp2[Env1] =
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
object Routes2                                                     {
  def apply[Env, Err](route: zio.http.Route[Env], routes: zio.http.Route[Env]*): Routes2[Env] =
    Routes2(Chunk(route) ++ Chunk.fromIterable(routes))
}
