package zio.http

/**
 * Thick protocol engine keyed on the Blocks HTTP [[Version]] sum type
 * (`HTTP/1.0`, `HTTP/1.1`, `HTTP/2.0`, `HTTP/3.0`).
 *
 * One engine serves exactly one version, and at most one engine per version may
 * be registered on a server: on Scala 3 the bound is enforced at compile time
 * by [[LoomServer.withEngine]] (`Tuple.Contains[Ps, P] =:= false`), so there is
 * no runtime duplicate-registration path and no `EngineId`/`ProtocolId`
 * registry. Engines never see each other's frames and are never discovered
 * reflectively: every engine serving a server is listed explicitly via
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
 *
 * The type parameter is covariant because engines are only ever read through it
 * (see [[EngineCoverage.check]]); use sites still name the exact served version
 * (the singleton type of the served `Version` case).
 */
trait ProtocolEngine[+P <: Version] {

  /** The exact HTTP version this engine serves. */
  def protocol: P

  /** Transport resource this engine can own connections on. */
  def transportKind: TransportKind

  /** Initiate graceful drain of owned connections. */
  def drain(): Unit

  /** Force-close owned connections immediately. */
  def close(): Unit
}

/**
 * Typed engine-registration failures.
 *
 * Carried as `Exception` subclasses so invalid registrations can also fail
 * `serve` fast, before any socket is bound. The only remaining failure is a
 * coverage gap: duplicate registration is impossible by construction
 * (compile-time max-one per version), so it has no runtime representation.
 */
sealed abstract class EngineRegistrationError(message: String) extends Exception(message) {
  override def getMessage: String = message
}

object EngineRegistrationError {

  /**
   * A served connector needs `version` but no registered engine claims it.
   * Register one with `LoomServer.withEngine` before `serve`.
   */
  final case class MissingEngine(version: Version)
      extends EngineRegistrationError(
        s"No protocol engine registered for HTTP version $version: register one with LoomServer.withEngine before serve",
      )
}

/**
 * Compile-time-keyed engine coverage for `serve`.
 *
 * Coverage is connector-driven and opt-in: a server with no explicitly
 * registered engines keeps the legacy bind path unchanged, while declaring any
 * engine opts the server into the typed contract — every served connector
 * version must then have a registered engine, checked before any socket is
 * bound (see [[EngineRegistrationError.MissingEngine]]). Extra engines no
 * connector needs are allowed.
 */
object EngineCoverage {

  /**
   * Total `connector -> Version` mapping over the current [[Protocol]] cases.
   *
   * `Protocol.H2C` and `Protocol.H2` both mean wire `HTTP/2.0`: TLS vs
   * cleartext is connector transport, not version. One `HTTP/2.0` engine
   * therefore serves both binds (dual-bind sharing drain/close), so two H2
   * connectors do NOT need two engines. `Protocol.H3` maps to `HTTP/3.0`, which
   * has no engine: at `serve` it is refused earlier by connector validation
   * (`ConnectorFailure.H3NotAdvertised`), while this mapping keeps the pure
   * function total with no H3 special case.
   *
   * There is deliberately no H1 mapping yet: `Connector.protocol` has no H1
   * case in this wave and no H1 engine or transport exists (both arrive with
   * the H1 wave), so H2C connectors are never silently treated as H1. An engine
   * may already claim `HTTP/1.1` (e.g. behind a config flag); coverage for H1
   * connectors activates with the H1 connector case. Unregistered versions
   * (`HTTP/1.0`, unclaimed `HTTP/1.1`, `HTTP/3.0`) simply have no engine.
   */
  def protocolVersion(protocol: Protocol): Version =
    protocol match {
      case Protocol.H2C(_)      => Version.`HTTP/2.0`
      case Protocol.H2(_, _)    => Version.`HTTP/2.0`
      case Protocol.H3(_, _, _) => Version.`HTTP/3.0`
    }

  /**
   * Checks that every connector version has a registered engine. Reports the
   * first uncovered version in connector order, so failures are reproducible.
   */
  def check(
    connectors: List[Connector],
    engines: List[ProtocolEngine[Version]],
  ): Either[EngineRegistrationError, Unit] = {
    val owned                    = engines.map(_.protocol).toSet
    var index                    = 0
    var missing: Option[Version] = None
    while (index < connectors.length && missing.isEmpty) {
      val version = protocolVersion(connectors(index).protocol)
      if (!owned.contains(version)) missing = Some(version)
      index += 1
    }
    missing match {
      case Some(version) => Left(EngineRegistrationError.MissingEngine(version))
      case None          => Right(())
    }
  }
}
