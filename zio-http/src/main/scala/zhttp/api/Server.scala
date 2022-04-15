package zhttp.api

import zio.{Has, ZIO}

final case class Server[R <: Has[_], E <: Throwable](apis: APIs, handlers: Handlers[R, E]) {
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
  def make[R <: Has[_], E <: Throwable](
    apis: APIs,
    handlers: Handlers[R, E],
  ): Server[R, E] =
    Server(apis, handlers)

  def start[R <: Has[_], E <: Throwable](
    port: Int,
    apis: APIs,
    handlers: Handlers[R, E],
  ): ZIO[R, Throwable, Nothing] =
    make(apis, handlers).start(port)
}
