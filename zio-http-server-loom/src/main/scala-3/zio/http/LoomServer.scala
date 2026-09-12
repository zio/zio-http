package zio.http

import scala.annotation.experimental

import zio.blocks.context.Context
import zio.http.h2.H2Transport

/**
 * Loom (virtual-thread, blocking) [[Server]] with explicitly registered thick
 * protocol engines.
 *
 * Engines are keyed on the Blocks HTTP [[Version]] sum type with a compile-time
 * max-one-per-version bound: [[withEngine]] requires
 * `Tuple.Contains[Ps, P] =:= false`, so registering two engines for the same
 * version does not compile and there is no runtime duplicate path. (A
 * `NotGiven[Tuple.Contains[Ps, P]]` bound would be vacuous here:
 * `Tuple.Contains` reduces to the `Boolean` types `true`/`false`, for which no
 * givens ever exist, so `NotGiven` would materialize in both cases and
 * constrain nothing. Requiring `=:= false` fails closed instead.) Each
 * conditional branch still checks max-one independently, so
 * `if (flag) server.withEngine(engine) else server` typechecks and serves in
 * both branches.
 *
 * @tparam Ps
 *   the tuple of engine versions registered so far, in most-recent-first order.
 */
@experimental
class LoomServer[Ps <: Tuple](
  connector: Connector,
  additionalConnectors: List[Connector] = Nil,
  defectHandler: DefectHandler = DefectHandler.default,
  engines: List[ProtocolEngine[Version]] = Nil,
) extends Server {

  def addConnector(c: Connector): LoomServer[Ps] =
    new LoomServer[Ps](connector, c :: additionalConnectors, defectHandler, engines)

  def withDefectHandler(h: DefectHandler): LoomServer[Ps] =
    new LoomServer[Ps](connector, additionalConnectors, h, engines)

  /**
   * Register the engine serving version `P`. At most one engine per version: a
   * second registration for an already-registered `P` is a compile-time error,
   * not a runtime failure.
   *
   * `Protocol.H2C` and `Protocol.H2` connectors share one `HTTP/2.0` engine
   * (TLS vs cleartext is connector transport, not version).
   */
  def withEngine[P <: Version](
    engine: ProtocolEngine[P],
  )(using Tuple.Contains[Ps, P] =:= false): LoomServer[P *: Ps] =
    new LoomServer[P *: Ps](connector, additionalConnectors, defectHandler, engine :: engines)

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
  def apply(connector: Connector = Connector.default): LoomServer[EmptyTuple] =
    new LoomServer[EmptyTuple](connector)
}
