package zio.http

import java.nio.file.Path
import java.nio.file.Paths

import zio.blocks.config.Secret
import zio.blocks.schema.Schema

private object ConnectorSchemas {
  implicit val pathSchema: Schema[Path]     = Schema[String].transform(Paths.get(_), _.toString)
  implicit val secretSchema: Schema[Secret] = Schema[String].transform(Secret(_), Secret.unwrap)
}

case class Connector(
  bind: BindAddress = BindAddress.Tcp(),
  protocol: Protocol = Protocol.H2C(),
  idleTimeout: java.time.Duration = java.time.Duration.ofSeconds(60),
  /**
   * Maximum size in bytes of a single request body accepted by the server.
   *
   * This is a general binding-level knob shared by the H2 and H3 transports:
   * wire-only settings stay on `Http2Config`/`Http3Config`. The H2 transport
   * accounts DATA-frame payloads incrementally against this cap while reading
   * the request body and resets the stream with
   * `RST_STREAM(FLOW_CONTROL_ERROR)` the moment the cap is exceeded, so an
   * over-cap body is never buffered in full. A declared `content-length` larger
   * than this cap is rejected before any body bytes are read.
   */
  maxRequestBodySize: Long = Connector.DefaultMaxRequestBodySize,
  /**
   * Whole-request deadline in milliseconds, measured from stream start until
   * the response completes.
   *
   * This is a general binding-level knob shared by the H2 and H3 transports:
   * wire-only settings stay on `Http2Config`/`Http3Config`. The H2 transport
   * forwards it to the per-stream request timer, which resets the stream with
   * `RST_STREAM(CANCEL)` on expiry. Non-positive disables. Default 30 seconds.
   */
  requestTimeoutMs: Long = Connector.DefaultRequestTimeoutMs,
  /**
   * Header-completion deadline in milliseconds, measured from stream start.
   *
   * This is a general binding-level knob shared by the H2 and H3 transports:
   * wire-only settings stay on `Http2Config`/`Http3Config`. It bounds the time
   * to receive a complete header block, including trailing CONTINUATION frames
   * (`H2Connection` pending headers). Expiry resets the stream with
   * `RST_STREAM(CANCEL)`. Non-positive disables. Default 5 seconds.
   */
  headerTimeoutMs: Long = Connector.DefaultHeaderTimeoutMs,
  /**
   * Body-completion (time-to-complete) deadline in milliseconds, measured from
   * stream start until the full request body is received.
   *
   * This is a general binding-level knob shared by the H2 and H3 transports:
   * wire-only settings stay on `Http2Config`/`Http3Config`. Total elapsed time
   * is enforced, not just the gap between frames, so a drip that keeps moving
   * but too slowly still times out (`H2Transport` body read). Expiry resets the
   * stream with `RST_STREAM(CANCEL)`. Non-positive disables. Default 10
   * seconds.
   */
  bodyTimeoutMs: Long = Connector.DefaultBodyTimeoutMs,
  /**
   * Which TCP peers are trusted to supply forwarding headers
   * (`X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host`, RFC 7239
   * `Forwarded`) to the loom HTTP/2 transport.
   *
   * This is a general binding-level knob shared by the H2 and H3 transports.
   * Default-deny: `TrustedProxyConfig.default` trusts nothing, so forwarding
   * headers from any peer are stripped with zero effect and the resolved client
   * IP falls back to the socket peer address.
   */
  trustedProxy: TrustedProxyConfig = TrustedProxyConfig.default,
) {
  if (maxRequestBodySize < 0L)
    throw new IllegalArgumentException("maxRequestBodySize must be non-negative")
}

object Connector {
  val default: Connector = Connector()

  /** Default request-body cap: 1 MiB per stream. */
  val DefaultMaxRequestBodySize: Long = 1024L * 1024L

  /** Default whole-request deadline: 30 seconds. */
  val DefaultRequestTimeoutMs: Long = 30000L

  /** Default header-completion deadline: 5 seconds. */
  val DefaultHeaderTimeoutMs: Long = 5000L

  /** Default body-completion (time-to-complete) deadline: 10 seconds. */
  val DefaultBodyTimeoutMs: Long = 10000L

  implicit val schema: Schema[Connector] = Schema.derived[Connector]
}

sealed trait BindAddress
object BindAddress {
  import ConnectorSchemas._

  case class Tcp(host: String = "0.0.0.0", port: Int = 8080) extends BindAddress
  case class Unix(path: Path)                                extends BindAddress

  def localhost(port: Int): BindAddress = Tcp("127.0.0.1", port)
  def anyHost(port: Int): BindAddress   = Tcp("0.0.0.0", port)

  implicit val tcpSchema: Schema[Tcp]      = Schema.derived[Tcp]
  implicit val unixSchema: Schema[Unix]    = Schema.derived[Unix]
  implicit val schema: Schema[BindAddress] = Schema.derived[BindAddress]
}

sealed trait Protocol
object Protocol {
  case class H2C(http2: Http2Config = Http2Config())                                                 extends Protocol
  case class H2(tls: TlsConfig, http2: Http2Config = Http2Config())                                  extends Protocol
  case class H3(tls: TlsConfig, quic: QuicConfig = QuicConfig(), http3: Http3Config = Http3Config()) extends Protocol

  implicit val h2cSchema: Schema[H2C]   = Schema.derived[H2C]
  implicit val h2Schema: Schema[H2]     = Schema.derived[H2]
  implicit val h3Schema: Schema[H3]     = Schema.derived[H3]
  implicit val schema: Schema[Protocol] = Schema.derived[Protocol]
}

case class Http2Config(
  maxConcurrentStreams: Int = 100,
  initialWindowSize: Int = 65535,
  maxFrameSize: Int = 16384,
  maxHeaderListSize: Int = 8192,
) {
  if (maxFrameSize < 16384 || maxFrameSize > 16777215)
    throw new IllegalArgumentException("maxFrameSize must be in [16384,16777215]")
  if (initialWindowSize < 0 || initialWindowSize.toLong > 2147483647L)
    throw new IllegalArgumentException("initialWindowSize must be in [0, 2147483647]")
}

object Http2Config {
  implicit val schema: Schema[Http2Config] = Schema.derived[Http2Config]
}

case class Http3Config(
  maxFieldSectionSize: Long = 8192,
  qpackMaxTableCapacity: Int = 4096,
  qpackBlockedStreams: Int = 100,
)

object Http3Config {
  implicit val schema: Schema[Http3Config] = Schema.derived[Http3Config]
}

case class QuicConfig(
  maxIdleTimeout: java.time.Duration = java.time.Duration.ofSeconds(30),
)

object QuicConfig {
  implicit val schema: Schema[QuicConfig] = Schema.derived[QuicConfig]
}

sealed trait TlsSource
object TlsSource {
  import ConnectorSchemas._

  case class FilePath(path: Path)                      extends TlsSource
  case class PemString(pem: Secret)                    extends TlsSource
  case class SslContext(ctx: javax.net.ssl.SSLContext) extends TlsSource

  implicit val filePathSchema: Schema[FilePath]   = Schema.derived[FilePath]
  implicit val pemStringSchema: Schema[PemString] = Schema.derived[PemString]

  private final case class LoadableTlsSource(
    path: Option[Path] = None,
    pem: Option[Secret] = None,
  )
  private object LoadableTlsSource {
    implicit val schema: Schema[LoadableTlsSource] = Schema.derived[LoadableTlsSource]
  }

  implicit val schema: Schema[TlsSource] =
    LoadableTlsSource.schema.transform(
      {
        case LoadableTlsSource(Some(path), None) => FilePath(path)
        case LoadableTlsSource(None, Some(pem))  => PemString(pem)
        case LoadableTlsSource(Some(_), Some(_)) =>
          throw new IllegalStateException("TlsSource config must provide either path or pem, not both")
        case LoadableTlsSource(None, None)       =>
          throw new IllegalStateException("TlsSource config must provide either path or pem")
      },
      {
        case FilePath(path) => LoadableTlsSource(path = Some(path))
        case PemString(pem) => LoadableTlsSource(pem = Some(pem))
        case SslContext(_)  =>
          throw new IllegalStateException("TlsSource.SslContext cannot be loaded from structured config")
      },
    )
}

/**
 * Server-side TLS ALPN acceptance policy.
 *
 * This controls whether the Loom H2 server rejects (at the TLS layer) clients
 * that do not negotiate `h2`, or accepts whatever ALPN protocol was negotiated.
 * The server itself stays H2-only: the policy adds no HTTP/1.1 fallback
 * handling, it only governs TLS rejection vs. acceptance.
 *
 * This server policy is independent from any future client-side
 * `ClientAlpnPolicy`: the two govern opposite ends of the handshake and must
 * stay separate types.
 */
sealed trait AlpnPolicy
object AlpnPolicy {

  /** Reject any connection that does not negotiate `h2` (current behavior). */
  case object StrictH2 extends AlpnPolicy

  /**
   * Accept the negotiated protocol; prefer `h2` via [[TlsConfig.alpnProtocols]]
   * order.
   */
  case object NegotiateH2Preferred extends AlpnPolicy

  implicit val strictH2Schema: Schema[StrictH2.type]                         = Schema.derived[StrictH2.type]
  implicit val negotiateH2PreferredSchema: Schema[NegotiateH2Preferred.type] =
    Schema.derived[NegotiateH2Preferred.type]
  implicit val schema: Schema[AlpnPolicy]                                    = Schema.derived[AlpnPolicy]
}

/**
 * H2-only server TLS identity: `alpnProtocols` order pins the preferred ALPN
 * protocol, `alpnPolicy` decides whether non-`h2` clients are rejected or
 * accepted (no HTTP/1.1 fallback either way), and `tlsVersions` pins the
 * negotiable TLS versions.
 */
case class TlsConfig(
  certChain: TlsSource,
  privateKey: TlsSource,
  alpnProtocols: List[String] = List("h2"),
  alpnPolicy: AlpnPolicy = AlpnPolicy.StrictH2,
  tlsVersions: List[String] = List("TLSv1.3", "TLSv1.2"),
  /**
   * Require the TLS peer to present a certificate (mutual TLS).
   *
   * The handshake aborts when the peer sends no (or an untrusted) certificate,
   * so an HAProxy-style sidecar identity can gate proxy trust (see
   * `TrustedProxyConfig.trustPeerCert`). Default `false`.
   */
  requireClientAuth: Boolean = false,
  /**
   * Extra CA certificates used to verify the peer's certificate when
   * `requireClientAuth` is set. When absent, the platform default trust store
   * is used. Never `null` trust managers: the loom listener always installs
   * real trust managers.
   */
  trustCertChain: Option[TlsSource] = None,
)

object TlsConfig {
  implicit val schema: Schema[TlsConfig] = Schema.derived[TlsConfig]
}

/**
 * Which TCP peers are trusted to supply forwarding headers (`X-Forwarded-For`,
 * `X-Forwarded-Proto`, `X-Forwarded-Host`, RFC 7239 `Forwarded`) to the loom
 * HTTP/2 transport.
 *
 * Default-deny: `TrustedProxyConfig.default` trusts nothing, so forwarding
 * headers from any peer are stripped with zero effect and the resolved client
 * IP falls back to the socket peer address.
 *
 * Allowlist format: each entry of `trustedCidrs` is either a literal IP address
 * (`"10.0.0.1"`, `"::1"`) for an exact match, or a CIDR range (`"10.0.0.0/8"`,
 * `"2001:db8::/32"`, `"127.0.0.1/32"`). The set is pre-parsed once into numeric
 * networks: entries that are not a literal IP or a strict CIDR — notably
 * hostnames — never match (fail closed) and are never resolved via DNS at
 * request time. IPv4 entries never match IPv6 peers and vice versa.
 *
 * @param trustedCidrs
 *   IP literals or CIDR ranges whose forwarding headers are honored.
 * @param trustPeerCert
 *   When `true`, any peer that authenticated with a client certificate (mTLS)
 *   is trusted regardless of the CIDR allowlist. Requires
 *   `TlsConfig.requireClientAuth` on the connector. Default `false`.
 */
case class TrustedProxyConfig(
  trustedCidrs: Set[String] = Set.empty,
  trustPeerCert: Boolean = false,
) {

  /**
   * The allowlist pre-parsed ONCE into numeric (address bytes, prefix bits)
   * networks. Entries that are not a literal IP or a strict CIDR (hostnames,
   * malformed ranges) are dropped here, so request-time matching never resolves
   * DNS and can never trust via spoofed name resolution.
   */
  private lazy val parsedNetworks: Set[(Array[Byte], Int)] =
    TrustedProxyConfig.parseNetworks(trustedCidrs)

  /**
   * Returns `true` iff forwarding headers from the peer at `peerIp` may be
   * applied. `hasPeerCert` reports whether the peer presented a verified client
   * certificate on this connection. `peerIp` comes from the socket address, so
   * it is parsed as a numeric literal only: unparseable values fail closed with
   * zero DNS resolution.
   */
  def isTrusted(peerIp: String, hasPeerCert: Boolean): Boolean =
    (trustPeerCert && hasPeerCert) || TrustedProxyConfig.matchesParsed(peerIp, parsedNetworks)
}

object TrustedProxyConfig {

  /** Default-deny: no peer is trusted. */
  val default: TrustedProxyConfig = TrustedProxyConfig()

  /** Normalized client-IP header set on every request for route handlers. */
  val ClientIpHeader: String = "x-client-ip"

  /** Socket peer-address header set on every request for route handlers. */
  val PeerAddressHeader: String = "x-peer-address"

  implicit val schema: Schema[TrustedProxyConfig] = Schema.derived[TrustedProxyConfig]

  private def matchesParsed(peerIp: String, networks: Set[(Array[Byte], Int)]): Boolean =
    parseLiteralIp(peerIp) match {
      case None       => false
      case Some(peer) =>
        val iterator = networks.iterator
        var matched  = false
        while (iterator.hasNext && !matched) {
          val (network, bits) = iterator.next()
          matched = matchesNetwork(network, peer, bits)
        }
        matched
    }

  private def parseNetworks(entries: Set[String]): Set[(Array[Byte], Int)] =
    entries.flatMap(parseEntry)

  /**
   * Parses one allowlist entry into (network bytes, prefix bits). Returns
   * `None` — never matching — for anything that is not a literal IP or a strict
   * `address/bits` CIDR. Never consults DNS (see `parseLiteralIp`).
   */
  private def parseEntry(entry: String): Option[(Array[Byte], Int)] = {
    val slash = entry.indexOf('/')
    if (slash < 0) parseLiteralIp(entry).map(bytes => (bytes, bytes.length * 8))
    else {
      val bitText = entry.substring(slash + 1)
      if (bitText.isEmpty || !bitText.forall(_.isDigit)) None
      else {
        val bits = bitText.toInt
        parseLiteralIp(entry.substring(0, slash)) match {
          case Some(network) if bits <= network.length * 8 => Some((network, bits))
          case _                                           => None
        }
      }
    }
  }

  /**
   * Parses a numeric IP literal into address bytes without DNS resolution.
   *
   * `InetAddress.getByName` is only reached for strings that are already proven
   * to be numeric: anything containing `:` cannot be a DNS name (hostnames
   * never contain colons), so it is parsed as an IPv6 literal with no lookup;
   * anything without a colon must be a strict dotted quad, otherwise it is
   * rejected before `getByName` could treat it as a hostname and consult DNS.
   * Unparseable input yields `None` (fail closed).
   */
  private def parseLiteralIp(text: String): Option[Array[Byte]] =
    try {
      if (text.isEmpty) None
      else if (text.contains(":")) {
        if (!text.forall(c => c.isDigit || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == ':' || c == '.'))
          None
        else Some(java.net.InetAddress.getByName(text).getAddress)
      } else {
        val parts = text.split("\\.", -1)
        if (parts.length != 4) None
        else {
          var index   = 0
          var numeric = true
          while (index < 4 && numeric) {
            val part = parts(index)
            if (part.isEmpty || part.length > 3 || !part.forall(_.isDigit)) numeric = false
            else {
              val value = part.toInt
              if (value < 0 || value > 255) numeric = false
            }
            index += 1
          }
          if (!numeric) None else Some(java.net.InetAddress.getByName(text).getAddress)
        }
      }
    } catch {
      case _: Exception => None
    }

  private def matchesNetwork(network: Array[Byte], peer: Array[Byte], bits: Int): Boolean = {
    if (network.length != peer.length || bits < 0 || bits > network.length * 8) false
    else {
      var index     = 0
      var matching  = true
      val fullBytes = bits / 8
      val restBits  = bits % 8
      while (index < fullBytes && matching) {
        if (network(index) != peer(index)) matching = false
        index += 1
      }
      if (matching && restBits > 0) {
        val mask = (0xff << (8 - restBits)) & 0xff
        matching = (network(index) & mask) == (peer(index) & mask)
      }
      matching
    }
  }
}
