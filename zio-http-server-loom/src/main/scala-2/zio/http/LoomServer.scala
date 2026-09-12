package zio.http

import scala.annotation.experimental

import zio.blocks.context.Context
import zio.http.h2.H2Transport

/**
 * Loom (virtual-thread, blocking) [[Server]] with explicitly registered thick
 * protocol engines.
 *
 * Scala 2 fallback: the Scala 3 tuple-keyed `LoomServer[Ps]` with its
 * compile-time max-one-per-version bound (`Tuple.Contains[Ps, P] =:= false`)
 * cannot be expressed in the Scala 2 shared sources, so this variant keeps the
 * same runtime behavior (connector validation, opt-in engine coverage, bind)
 * with a plain registration method. Max-one per version is enforced at compile
 * time on Scala 3 only; on Scala 2 duplicate versions are a programming error
 * with no runtime representation.
 */
@experimental
class LoomServer(
  connector: Connector,
  additionalConnectors: List[Connector] = Nil,
  defectHandler: DefectHandler = DefectHandler.default,
  engines: List[ProtocolEngine[Version]] = Nil,
) extends Server {

  def addConnector(c: Connector): LoomServer =
    new LoomServer(connector, c :: additionalConnectors, defectHandler, engines)

  def withDefectHandler(h: DefectHandler): LoomServer =
    new LoomServer(connector, additionalConnectors, h, engines)

  /**
   * Register the engine serving a version. On Scala 3 prefer the typed
   * overload, which rejects a second engine for the same version at compile
   * time.
   *
   * `Protocol.H2C` and `Protocol.H2` connectors share one `HTTP/2.0` engine
   * (TLS vs cleartext is connector transport, not version).
   */
  def withEngine(engine: ProtocolEngine[Version]): LoomServer =
    new LoomServer(connector, additionalConnectors, defectHandler, engine :: engines)

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
    // Typed coverage (opt-in): declaring any engine opts into the contract, so
    // every served connector version must then have a registered engine. A
    // server with no engines keeps the legacy bind path unchanged.
    if (engines.nonEmpty)
      EngineCoverage.check(allConnectors, engines) match {
        case Left(error) => throw error
        case Right(_)    => ()
      }
    val bound         = allConnectors.map { c =>
      new H2Transport(routes, context, c, defectHandler).start()
    }
    ServerHandle.live(bound)
  }
}

@experimental
object LoomServer {
  def apply(connector: Connector = Connector.default): LoomServer =
    new LoomServer(connector)
}
