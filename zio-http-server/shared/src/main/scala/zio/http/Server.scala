package zio.http

import zio.blocks.context.Context

final class Server[Ctx] private (
  val routes: Routes[Ctx],
  private val _connectors: List[Connector],
  val routeDefectHandler: DefectHandler,
  val gracefulShutdownTimeout: java.time.Duration,
) {
  def addConnector(connector: Connector): Server[Ctx] =
    new Server(routes, _connectors :+ connector, routeDefectHandler, gracefulShutdownTimeout)

  def connectors(connectors: List[Connector]): Server[Ctx] = {
    require(connectors.nonEmpty, "At least one connector is required")
    new Server(routes, connectors, routeDefectHandler, gracefulShutdownTimeout)
  }

  def routeDefectHandler(handler: DefectHandler): Server[Ctx] =
    new Server(routes, _connectors, handler, gracefulShutdownTimeout)

  def gracefulShutdown(timeout: java.time.Duration): Server[Ctx] =
    new Server(routes, _connectors, routeDefectHandler, timeout)

  def serve(context: Context[Ctx]): ServerHandle =
    ServerRuntime.serve(this, context)

  private[http] def connectorsList: List[Connector] = _connectors

  override def toString: String =
    s"Server(${_connectors.size} connectors, ${routes.size} routes)"
}

object Server {
  def apply[Ctx](
    routes: Routes[Ctx],
    connector: Connector = Connector.default,
  ): Server[Ctx] =
    new Server(routes, List(connector), DefectHandler.default, java.time.Duration.ofSeconds(30))
}
