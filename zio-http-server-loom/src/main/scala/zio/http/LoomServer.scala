package zio.http

import zio.blocks.context.Context
import zio.http.h2.H2Transport

/**
 * Loom (virtual-thread, blocking) [[Server]].
 *
 * Users configure connectors only: `LoomServer(connector, additional*)`
 * requires at least one connector and takes no engines. At serve time each
 * connector is model-validated, then the matching built-in engine object is
 * selected from its version — one stateless `H2Engine` serves both H2C and H2
 * (TLS vs cleartext is connector transport, not version). Versions without an
 * engine (currently H3) fail before any socket is bound.
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
    // from H2Engine/H2Transport construction.
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
 * Package-private engine selection: connectors in, bound transports out.
 *
 * H2C and H2 connectors resolve to the single stateless [[H2Engine]]; H3 has a
 * connector type but no engine yet and is refused before bind (connector
 * validation reports the same failure first for validated serves).
 */
private[http] object HttpEngine {

  def engineFor(connector: Connector): H2Engine.type =
    connector.protocol match {
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
private[http] object H2Engine {

  def bind[Ctx](
    routes: Routes[Ctx],
    context: Context[Ctx],
    connector: Connector,
    defectHandler: DefectHandler,
  ): BoundConnectorHandle =
    new H2Transport(routes, context, connector, defectHandler).start()
}
