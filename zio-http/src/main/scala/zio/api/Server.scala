package zhttp.api

import zio.ZIO

final case class Server[R, E <: Throwable](apis: APIs, handlers: Handlers[R, E]) {
  import zhttp.http._

  def start(port: Int): ZIO[R, Throwable, Nothing] =
    zhttp.service.Server.start(port, toHttpApp ++ Http.notFound)

  private def toHttpApp: HttpApp[R, E] =
    handlers.toList.map { handler =>
      ServerInterpreter.handlerToHttpApp(handler)
    }
      .reduce(_ ++ _)
}

object Server {
  def make[R, E <: Throwable](
    apis: APIs,
    handlers: Handlers[R, E],
  ): Server[R, E] =
    Server(apis, handlers)

  def start[R, E <: Throwable](
    port: Int,
    apis: APIs,
    handlers: Handlers[R, E],
  ): ZIO[R, Throwable, Nothing] =
    make(apis, handlers).start(port)
}
