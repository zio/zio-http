package zio.http

import java.time.Duration

import zio.blocks.schema.Schema

/**
 * Configuration for the HTTP client.
 *
 * Explicit config surfaces for drivers (notably T14's LoomH2 driver, which
 * reuses h2-codec): [[tls]] selects trust/key material and pins TLS
 * versions, [[alpn]] selects the client's ALPN offer policy, [[pool]] sizes
 * the connection pool, and [[deadline]] carries optional per-stage deadline
 * overrides. Drivers must read timeouts through [[effectiveConnectTimeout]] /
 * [[effectiveRequestTimeout]] / [[effectiveStreamTimeout]] so override
 * precedence lives in exactly one place.
 *
 * Load from an external source via:
 * {{{
 *   import zio.blocks.config.{Config, ConfigSource}
 *   val cfg: Either[_, ClientConfig] =
 *     Config.load[ClientConfig](ConfigSource.fromSystemProperties())
 * }}}
 */
final case class ClientConfig(
  connectTimeout: Duration = Duration.ofSeconds(10),
  requestTimeout: Duration = Duration.ofSeconds(30),
  followRedirects: Boolean = true,
  maxRedirects: Int = 5,
  tls: Option[ClientTlsConfig] = None,
  alpn: ClientAlpnPolicy = ClientAlpnPolicy.H2PreferredWithH11Fallback,
  pool: PoolConfig = PoolConfig(),
  deadline: DeadlineConfig = DeadlineConfig(),
) {
  if (maxRedirects < 0)
    throw new IllegalArgumentException(s"ClientConfig.maxRedirects must be >= 0, got $maxRedirects")

  /** Deadline override wins, else the legacy top-level [[connectTimeout]]. */
  def effectiveConnectTimeout: Duration =
    deadline.connectTimeout.getOrElse(connectTimeout)

  /** Deadline override wins, else the legacy top-level [[requestTimeout]]. */
  def effectiveRequestTimeout: Duration =
    deadline.requestTimeout.getOrElse(requestTimeout)

  /** Per-stream deadline; `None` (the default) means disabled. */
  def effectiveStreamTimeout: Option[Duration] =
    deadline.streamTimeout
}

object ClientConfig {
  implicit val schema: Schema[ClientConfig] = Schema.derived[ClientConfig]
}
