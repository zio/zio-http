package zio.http

import zio.blocks.context.Context
import zio.http.h2.H2Transport

/**
 * Loom (virtual-thread, blocking) [[Server]] with explicitly registered thick
 * protocol engines.
 *
 * Engines are keyed on the Blocks HTTP [[Version]] sum type and listed once at
 * construction, with a compile-time max-one-per-version bound: the fixed
 * [[LoomServer.apply]] overloads require pairwise `=!=` evidence, so listing
 * two engines for the same version does not compile. There is no runtime
 * duplicate path and no zero-engine public state: every `apply` overload takes
 * at least one engine.
 *
 * The primary constructor is private: servers come only from the fixed `apply`
 * overloads, so a server with zero engines is unrepresentable, not merely
 * undocumented.
 */
class LoomServer private (
  connector: Connector,
  additionalConnectors: List[Connector],
  defectHandler: DefectHandler,
  engines: List[ProtocolEngine[Version]],
) extends Server {

  def addConnector(c: Connector): LoomServer =
    new LoomServer(connector, c :: additionalConnectors, defectHandler, engines)

  def withDefectHandler(h: DefectHandler): LoomServer =
    new LoomServer(connector, additionalConnectors, h, engines)

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
    // Typed coverage: every served connector version must have a registered
    // engine — construction requires at least one engine, so the check always
    // runs. Uncovered versions fail before bind with `MissingEngine`.
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

object LoomServer {

  /** One engine: the minimal serving shape. */
  def apply[P <: Version](connector: Connector, e1: ProtocolEngine[P]): LoomServer =
    new LoomServer(connector, Nil, DefectHandler.default, List(e1))

  /** Two engines of distinct versions. */
  def apply[P <: Version, Q <: Version](connector: Connector, e1: ProtocolEngine[P], e2: ProtocolEngine[Q])(implicit
    ev: P =!= Q,
  ): LoomServer =
    new LoomServer(connector, Nil, DefectHandler.default, List(e1, e2))

  /** Three engines of pairwise distinct versions. */
  def apply[P <: Version, Q <: Version, R <: Version](
    connector: Connector,
    e1: ProtocolEngine[P],
    e2: ProtocolEngine[Q],
    e3: ProtocolEngine[R],
  )(implicit
    ev1: P =!= Q,
    ev2: P =!= R,
    ev3: Q =!= R,
  ): LoomServer =
    new LoomServer(connector, Nil, DefectHandler.default, List(e1, e2, e3))

  /**
   * Four engines of pairwise distinct versions: the maximum, since the Blocks
   * [[Version]] sum type has exactly four cases.
   */
  def apply[P <: Version, Q <: Version, R <: Version, S <: Version](
    connector: Connector,
    e1: ProtocolEngine[P],
    e2: ProtocolEngine[Q],
    e3: ProtocolEngine[R],
    e4: ProtocolEngine[S],
  )(implicit
    ev1: P =!= Q,
    ev2: P =!= R,
    ev3: P =!= S,
    ev4: Q =!= R,
    ev5: Q =!= S,
    ev6: R =!= S,
  ): LoomServer =
    new LoomServer(connector, Nil, DefectHandler.default, List(e1, e2, e3, e4))
}

/**
 * Type inequality used by the fixed engine-list [[LoomServer.apply]] overloads.
 *
 * Deliberately local (same syntax as `zio.=!=`, but core server packages take
 * on no new dependency for this). Proved by implicit ambiguity: `neq` applies
 * for any pair while the two `neqAmbig` instances also apply when both sides
 * are equal, so duplicates never summon. Identical mechanics on Scala 2.13 and
 * Scala 3, with no version-split helpers, facsimiles, or match types.
 */
sealed trait =!=[A, B]

object =!= {
  implicit def neq[A, B]: A =!= B = new =!=[A, B] {}

  implicit def neqAmbig1[A]: A =!= A = null

  implicit def neqAmbig2[A]: A =!= A = null
}
