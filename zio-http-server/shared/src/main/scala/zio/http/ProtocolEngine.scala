package zio.http

/**
 * Application protocol spoken by a [[ProtocolEngine]].
 *
 * One protocol is served by exactly one engine per registry: duplicate
 * registrations are rejected deterministically by [[EngineRegistry.build]].
 *
 * There is deliberately no H3 member: H3/QUIC has no installed engine, so no
 * engine may claim it and production configuration must neither advertise nor
 * run it (see [[ConnectorFailure.H3NotAdvertised]]). Transport families are
 * described by the shared [[TransportKind]] contract.
 */
sealed trait ProtocolId extends Product with Serializable

object ProtocolId {
  case object Http1 extends ProtocolId
  case object H2C   extends ProtocolId
  case object H2    extends ProtocolId
}

/** Unique identity of a registered [[ProtocolEngine]]. */
final case class EngineId(value: String) extends AnyVal

/**
 * Thick protocol engine: owns its wire semantics and its connections.
 *
 * Engines never see each other's frames and are never discovered reflectively:
 * every engine serving a registry is listed explicitly via
 * [[LoomServer.withEngine]]. Application behavior stays defined once, in
 * `Server.serve(routes, context)`; engines share one [[EngineDispatcher]] for
 * route handling.
 *
 * Shutdown coordination belongs to the server owner; engines expose only the
 * per-engine hooks:
 *
 *   - [[drain]]: stop accepting new work on owned connections and finish
 *     in-flight work promptly.
 *   - [[close]]: force-close owned connections immediately.
 */
trait ProtocolEngine {

  /** Unique identity of this engine. */
  def id: EngineId

  /** Transport resource this engine can own connections on. */
  def transportKind: TransportKind

  /** Application protocols this engine serves. */
  def supportedProtocols: Set[ProtocolId]

  /** Initiate graceful drain of owned connections. */
  def drain(): Unit

  /** Force-close owned connections immediately. */
  def close(): Unit
}

/**
 * Typed engine-registration failures.
 *
 * All failures are deterministic values: the same registration input always
 * yields the same error, in registration order. Carried as `Exception`
 * subclasses so invalid registrations can also fail `serve` fast, before any
 * socket is bound.
 */
sealed abstract class EngineRegistrationError(message: String) extends Exception(message) {
  override def getMessage: String = message
}

object EngineRegistrationError {

  /** Two engines share one [[EngineId]]. */
  final case class DuplicateEngineId(id: EngineId)
      extends EngineRegistrationError(s"Duplicate protocol engine id: ${id.value}")

  /**
   * Two engines claim one [[ProtocolId]]; each protocol has exactly one owner.
   */
  final case class DuplicateProtocol(protocol: ProtocolId, first: EngineId, second: EngineId)
      extends EngineRegistrationError(
        s"Duplicate protocol $protocol: claimed by ${first.value} and ${second.value}",
      )

  /** An engine's transport does not match the registry's transport. */
  final case class IncompatibleTransport(engine: EngineId, expected: TransportKind, actual: TransportKind)
      extends EngineRegistrationError(
        s"Engine ${engine.value} uses transport $actual but the registry expects $expected",
      )

  /** A registry must contain at least one engine. */
  case object EmptyRegistry extends EngineRegistrationError("Engine registry must contain at least one engine")
}

/**
 * Validated set of engines serving one application definition.
 *
 * Build with [[EngineRegistry.build]]: checks run in a fixed order (empty,
 * duplicate ids, incompatible transports, duplicate protocols) and report the
 * first violation in registration order, so failures are reproducible.
 *
 * Transport coexistence: TCP and UDP engines may share one registry because
 * they draw numeric ports from independent OS namespaces — a future UDP
 * (QUIC/H3) engine coexists with TCP engines on one numeric port. Unix-domain
 * sockets share neither namespace and stay exclusive with every other kind.
 */
final class EngineRegistry private (val engines: List[ProtocolEngine]) {

  /** Every protocol served by this registry, each with exactly one owner. */
  val protocols: Set[ProtocolId] =
    engines.foldLeft(Set.empty[ProtocolId])(_ ++ _.supportedProtocols)

  /** The engine owning `protocol`, if registered. */
  def engineFor(protocol: ProtocolId): Option[ProtocolEngine] =
    engines.find(_.supportedProtocols.contains(protocol))
}

object EngineRegistry {

  /**
   * True when two engine transport kinds may share one registry: identical
   * kinds, or the TCP/UDP socket pair whose numeric-port namespaces are
   * independent. Unix-domain sockets bind paths rather than ports and coexist
   * with nothing.
   */
  private def coexistsWith(first: TransportKind, second: TransportKind): Boolean =
    (first == second) || (isSocketFamily(first) && isSocketFamily(second))

  private def isSocketFamily(kind: TransportKind): Boolean =
    (kind == TransportKind.Tcp) || (kind == TransportKind.Udp)

  def build(engines: List[ProtocolEngine]): Either[EngineRegistrationError, EngineRegistry] = {
    if (engines.isEmpty) return Left(EngineRegistrationError.EmptyRegistry)

    val seenIds = scala.collection.mutable.Set.empty[EngineId]
    var index   = 0
    while (index < engines.length) {
      val id = engines(index).id
      if (!seenIds.add(id)) return Left(EngineRegistrationError.DuplicateEngineId(id))
      index += 1
    }

    val expected = engines.head.transportKind
    index = 0
    while (index < engines.length) {
      val engine = engines(index)
      if (!coexistsWith(expected, engine.transportKind))
        return Left(EngineRegistrationError.IncompatibleTransport(engine.id, expected, engine.transportKind))
      index += 1
    }

    val owners = scala.collection.mutable.Map.empty[ProtocolId, EngineId]
    index = 0
    while (index < engines.length) {
      val engine    = engines(index)
      val protocols = engine.supportedProtocols.toList.sortBy(_.toString)
      var j         = 0
      while (j < protocols.length) {
        val protocol = protocols(j)
        owners.get(protocol) match {
          case Some(first) => return Left(EngineRegistrationError.DuplicateProtocol(protocol, first, engine.id))
          case None        => owners.put(protocol, engine.id)
        }
        j += 1
      }
      index += 1
    }

    Right(new EngineRegistry(engines))
  }
}
