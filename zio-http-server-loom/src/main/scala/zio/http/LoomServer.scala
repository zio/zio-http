package zio.http

import zio.blocks.context.Context
import zio.http.h2.H2Transport

/**
 * Loom (virtual-thread, blocking) [[Server]] with explicitly registered thick
 * protocol engines.
 *
 * Construction is connectors-first in two phases: `LoomServer.connectors` lists
 * bindings and accumulates the required version set at type level, then
 * `serveWith` supplies engines once and compiles only when the supplied
 * versions match the required set exactly — no missing, no extras (a duplicate
 * is an extra of an already-supplied version, caught by pairwise `=!=`). The
 * primary constructor is private and there is no zero-connector entry and no
 * zero-engine overload, so incomplete servers are unrepresentable.
 */
class LoomServer private (
  connector: Connector,
  additionalConnectors: List[Connector],
  defectHandler: DefectHandler,
  engines: List[ProtocolEngine[Version]],
) extends Server {

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
    val bound         = allConnectors.map { c =>
      new H2Transport(routes, context, c, defectHandler).start()
    }
    ServerHandle.live(bound)
  }
}

object LoomServer {

  /** Phase 1: list the first connector; its version starts the required set. */
  def connectors(c1: H2CConnector): Requires[Version.`HTTP/2.0`.type] =
    new Requires(c1, Nil)

  /** Phase 1: list the first connector; its version starts the required set. */
  def connectors(c1: H2Connector): Requires[Version.`HTTP/2.0`.type] =
    new Requires(c1, Nil)

  /** Phase 1: list the first connector; its version starts the required set. */
  def connectors(c1: H3Connector): Requires[Version.`HTTP/3.0`.type] =
    new Requires(c1, Nil)

  /**
   * Phase 1 remainder: the listed connectors and their required version set.
   *
   * @tparam R
   *   the intersection of required wire versions so far.
   */
  final class Requires[R <: Version] private[LoomServer] (first: Connector, rest: List[Connector]) {

    /** List another H2C connector; the required set gains `HTTP/2.0`. */
    def addConnector(c: H2CConnector): Requires[R with Version.`HTTP/2.0`.type] =
      new Requires(first, c :: rest)

    /** List another H2 connector; the required set gains `HTTP/2.0`. */
    def addConnector(c: H2Connector): Requires[R with Version.`HTTP/2.0`.type] =
      new Requires(first, c :: rest)

    /** List another H3 connector; the required set gains `HTTP/3.0`. */
    def addConnector(c: H3Connector): Requires[R with Version.`HTTP/3.0`.type] =
      new Requires(first, c :: rest)

    /**
     * Phase 2: supply the one engine covering the required version. Compiles
     * only when the required set is exactly that version.
     */
    def serveWith[Q1 <: Version](e1: ProtocolEngine[Q1])(implicit
      ev1: R <:< Q1,
      ev2: Q1 <:< R,
    ): LoomServer =
      new LoomServer(first, rest, DefectHandler.default, List(e1))

    /**
     * Phase 2: supply the two engines covering the required versions. Compiles
     * only when the required set is exactly those two versions.
     */
    def serveWith[Q1 <: Version, Q2 <: Version](e1: ProtocolEngine[Q1], e2: ProtocolEngine[Q2])(implicit
      ev0: Q1 =!= Q2,
      ev1: R <:< (Q1 with Q2),
      ev2: (Q1 with Q2) <:< R,
    ): LoomServer =
      new LoomServer(first, rest, DefectHandler.default, List(e1, e2))

    /**
     * Phase 2, three engines: rejection-only — at most two wire versions are
     * expressible, so any three-engine list repeats a version and the pairwise
     * evidence refuses it. Exists so ABA duplicates fail with a duplicate error
     * rather than an arity error.
     */
    def serveWith[Q1 <: Version, Q2 <: Version, Q3 <: Version](
      e1: ProtocolEngine[Q1],
      e2: ProtocolEngine[Q2],
      e3: ProtocolEngine[Q3],
    )(implicit
      ev01: Q1 =!= Q2,
      ev02: Q1 =!= Q3,
      ev12: Q2 =!= Q3,
      ev1: R <:< (Q1 with Q2 with Q3),
      ev2: (Q1 with Q2 with Q3) <:< R,
    ): LoomServer =
      new LoomServer(first, rest, DefectHandler.default, List(e1, e2, e3))
  }
}

/**
 * Type inequality used by the phased engine-list `serveWith` methods.
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
