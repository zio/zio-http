package zio.http

/**
 * Thick protocol engine keyed on the Blocks HTTP [[Version]] sum type
 * (`HTTP/1.0`, `HTTP/1.1`, `HTTP/2.0`, `HTTP/3.0`).
 *
 * One engine serves exactly one version, and at most one engine per version may
 * be registered on a server: the phased `LoomServer` construction enforces the
 * bound at compile time (pairwise `=!=` evidence over the exact required set),
 * so there is no runtime duplicate-registration path and no
 * `EngineId`/`ProtocolId` registry. Engines never see each other's frames and
 * are never discovered reflectively: every engine serving a server is listed
 * explicitly when the server is built. Application behavior stays defined once,
 * in `Server.serve(routes, context)`; engines share one [[EngineDispatcher]]
 * for route handling.
 *
 * Shutdown coordination belongs to the server owner; engines expose only the
 * per-engine hooks:
 *
 *   - [[drain]]: stop accepting new work on owned connections and finish
 *     in-flight work promptly.
 *   - [[close]]: force-close owned connections immediately.
 *
 * The type parameter is covariant because engines are only ever read through
 * it; use sites still name the exact served version (the singleton type of the
 * served `Version` case).
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
