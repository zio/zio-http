package zhttp.api

import zio.ZIO

final case class Server[R, E <: Throwable](apis: APIs[_], handlers: Handlers[R, E]) {
  import zhttp.http._

  def start(port: Int): ZIO[R, Throwable, Nothing] =
    zhttp.service.Server.start(port, toHttpApp ++ Http.notFound)

  private def toHttpApp: HttpApp[R, E] =
    Handlers
      .handlersToList(handlers)
      .map { handler =>
        ServerInterpreter.handlerToHttpApp(handler)
      }
      .reduce(_ ++ _)
}

object Server {
  def make[R, E <: Throwable, ApiIds, HandlerIds](
    apis: APIs[ApiIds],
    handlers: Handlers[R, E],
  ): Server[R, E] =
    Server(apis, handlers)

  def start[R, E <: Throwable, ApiIds, HandlerIds](
    port: Int,
    apis: APIs[ApiIds],
    handlers: Handlers[R, E],
  ): ZIO[R, Throwable, Nothing] =
    make(apis, handlers).start(port)
}
