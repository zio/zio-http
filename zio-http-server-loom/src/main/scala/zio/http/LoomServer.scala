package zio.http

import zio.blocks.context.Context
import zio.http.h2.H2Transport

/**
 * Loom (virtual-thread, blocking) [[Server]].
 *
 * Users configure connectors only: `LoomServer(connector, additional*)`
 * requires at least one connector and takes no engines. At serve time each
 * connector is model-validated, then the matching built-in engine object is
 * selected from its application protocol — one stateless `H2Engine` serves both
 * H2C and H2 (TLS vs cleartext is connector transport, not version), while
 * cleartext `H1C` selects the stateless `H1Engine`. H3 has no engine and fails
 * before any socket is bound.
 *
 * The primary constructor is private: servers come only from `apply`, so a
 * server with zero connectors is unrepresentable, not merely undocumented.
 */
class LoomServer private (
  connector: Connector,
  additionalConnectors: List[Connector],
  defectHandler: DefectHandler,
) extends Server {

  def withDefectHandler(h: DefectHandler): LoomServer =
    new LoomServer(connector, additionalConnectors, h)

  override def serve[Ctx](routes: Routes[Ctx], context: Context[Ctx]): ServerHandle = {
    val allConnectors = connector :: additionalConnectors
    // Fail-before-bind: every connector is model-validated (H3, UDP, policy)
    // before any socket is opened. InvalidConnector wraps the typed
    // ConnectorFailure so callers never see raw UnsupportedOperationException
    // from engine/transport construction.
    allConnectors.foreach { c =>
      c.validate match {
        case Left(failure) => throw InvalidConnector(failure)
        case Right(_)      => ()
      }
    }
    val bound         = allConnectors.map { c =>
      HttpEngine.bind(routes, context, c, defectHandler)
    }
    ServerHandle.live(bound)
  }
}

object LoomServer {

  /** List one or more connectors; engines are selected internally at serve. */
  def apply(connector: Connector, additional: Connector*): LoomServer =
    new LoomServer(connector, additional.toList, DefectHandler.default)
}

/**
 * Package-private engine seam: every selected server engine binds one connector
 * to one [[BoundConnectorHandle]].
 *
 * Selection lives in [[HttpEngine.engineFor]]; the trait keeps the selection
 * site independent of each engine's implementation, so the H1 engine can land
 * without changing dispatch.
 */
private[http] sealed trait HttpServerEngine {
  def bind[Ctx](
    routes: Routes[Ctx],
    context: Context[Ctx],
    connector: Connector,
    defectHandler: DefectHandler,
  ): BoundConnectorHandle
}

/**
 * Package-private engine selection: connectors in, bound transports out.
 *
 * H2C and H2 connectors resolve to the single stateless [[H2Engine]]; cleartext
 * H1C resolves to [[H1Engine]]; H3 has a connector type but no engine yet and
 * is refused before bind (connector validation reports the same failure first
 * for validated serves).
 */
private[http] object HttpEngine {

  def engineFor(connector: Connector): HttpServerEngine =
    connector.protocol match {
      case Protocol.H1C()                      => H1Engine
      case Protocol.H2C(_) | Protocol.H2(_, _) => H2Engine
      case Protocol.H3(_, _, _)                => throw InvalidConnector(ConnectorFailure.H3NotAdvertised)
    }

  def bind[Ctx](
    routes: Routes[Ctx],
    context: Context[Ctx],
    connector: Connector,
    defectHandler: DefectHandler,
  ): BoundConnectorHandle =
    engineFor(connector).bind(routes, context, connector, defectHandler)
}

/**
 * The single stateless H2 engine: serves H2C and H2 bindings alike by
 * delegating to the existing [[H2Transport]]. Carries no configuration; all
 * tuning stays connector-scoped.
 */
private[http] object H2Engine extends HttpServerEngine {

  override def bind[Ctx](
    routes: Routes[Ctx],
    context: Context[Ctx],
    connector: Connector,
    defectHandler: DefectHandler,
  ): BoundConnectorHandle =
    new H2Transport(routes, context, connector, defectHandler).start()
}

/**
 * The single stateless H1 engine: selected for cleartext strict HTTP/1.1
 * (`Protocol.H1C`) bindings. Delegates to the strict-codec
 * [[zio.http.h1.H1Transport]], which binds the cleartext listener and serves
 * sequential keep-alive connections on Loom virtual threads; listener close is
 * the shutdown path.
 */
private[http] object H1Engine extends HttpServerEngine {

  override def bind[Ctx](
    routes: Routes[Ctx],
    context: Context[Ctx],
    connector: Connector,
    defectHandler: DefectHandler,
  ): BoundConnectorHandle =
    new zio.http.h1.H1Transport(routes, context, connector, defectHandler).start()
}
