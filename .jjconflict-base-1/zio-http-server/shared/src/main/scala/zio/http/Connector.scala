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
 * Allowlist format: each entry of `trustedCidrs` is either a numeric IP literal
 * (`"10.0.0.1"`, `"::1"`) for an exact match, or a numeric CIDR range
 * (`"10.0.0.0/8"`, `"2001:db8::/32"`, `"127.0.0.1/32"`). Numeric-only and
 * fail-closed: entries that are not a literal IPv4 dotted quad or a literal
 * IPv6 address (full or `::`-compressed, with IPv4-mapped normalization) —
 * notably hostnames and malformed ranges — never match and never trigger name
 * resolution. No DNS lookup ever happens, on any platform. IPv4 entries never
 * match IPv6 peers and vice versa.
 *
 * Platform-portable: matching uses a hand-rolled numeric parser with
 * indexOf-scan loops only, so shared sources cross-build where name-resolution
 * APIs are absent.
 *
 * @param trustedCidrs
 *   Numeric IP literals or CIDR ranges whose forwarding headers are honored.
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
   * networks. Non-numeric entries (hostnames, malformed ranges) are dropped
   * here, so request-time matching performs zero name resolution and can never
   * trust via spoofed name resolution.
   */
  private lazy val parsedNetworks: Array[(Array[Byte], Int)] =
    TrustedProxyConfig.parseNetworks(trustedCidrs)

  /**
   * Returns `true` iff forwarding headers from the peer at `peerIp` may be
   * applied. `hasPeerCert` reports whether the peer presented a verified client
   * certificate on this connection. `peerIp` comes from the socket address, so
   * it is parsed as a numeric literal only: unparseable values fail closed with
   * zero name resolution.
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

  private def matchesParsed(peerIp: String, networks: Array[(Array[Byte], Int)]): Boolean =
    parseLiteralIp(peerIp) match {
      case None       => false
      case Some(peer) =>
        var index   = 0
        var matched = false
        while (index < networks.length && !matched) {
          val network = networks(index)._1
          val bits    = networks(index)._2
          if (matchesNetwork(network, peer, bits)) matched = true
          index += 1
        }
        matched
    }

  private def parseNetworks(entries: Set[String]): Array[(Array[Byte], Int)] =
    entries.flatMap(parseEntry).toArray

  /**
   * Parses one allowlist entry into (network bytes, prefix bits). Returns
   * `None` — never matching — for anything that is not a numeric IP literal or
   * a strict `address/bits` CIDR. Never performs name resolution (see
   * `parseLiteralIp`).
   */
  private def parseEntry(entry: String): Option[(Array[Byte], Int)] = {
    val slash = entry.indexOf('/')
    if (slash < 0) parseLiteralIp(entry).map(bytes => (bytes, bytes.length * 8))
    else {
      var i    = slash + 1
      val end  = entry.length
      if (i >= end) return None
      var bits = 0
      while (i < end) {
        val c = entry.charAt(i)
        if (c < '0' || c > '9') return None
        bits = bits * 10 + (c - '0')
        if (bits > 128) return None
        i += 1
      }
      parseLiteralIp(entry.substring(0, slash)) match {
        case Some(network) if bits <= network.length * 8 => Some((network, bits))
        case _                                           => None
      }
    }
  }

  /**
   * Parses a numeric IP literal into address bytes with zero name resolution.
   *
   * Only strict dotted-decimal IPv4 quads and IPv6 literals (full or
   * `::`-compressed, with dotted-quad tails for mapped addresses) are accepted;
   * IPv4-mapped IPv6 (`::ffff:a.b.c.d`) normalizes to 4-byte IPv4, matching
   * prior address-bytes behavior. Anything else — hostnames, malformed input —
   * yields `None` (fail closed). Implemented with indexOf scan loops only, so
   * it cross-builds to platforms without resolver APIs.
   */
  private def parseLiteralIp(text: String): Option[Array[Byte]] = {
    if (text.isEmpty) None
    else if (text.indexOf(':') >= 0) parseIPv6(text)
    else parseIPv4(text)
  }

  private def parseIPv4(text: String): Option[Array[Byte]] = {
    val len       = text.length
    if (len == 0 || len > 15) return None
    val out       = new Array[Byte](4)
    var start     = 0
    var partIndex = 0
    while (partIndex < 4) {
      var end     = start
      while (end < len && text.charAt(end) != '.') end += 1
      val partLen = end - start
      if (partLen == 0 || partLen > 3) return None
      var value   = 0
      var k       = start
      while (k < end) {
        val c = text.charAt(k)
        if (c < '0' || c > '9') return None
        value = value * 10 + (c - '0')
        k += 1
      }
      if (value > 255) return None
      out(partIndex) = value.toByte
      partIndex += 1
      if (partIndex < 4) {
        if (end >= len || text.charAt(end) != '.') return None
        start = end + 1
      } else if (end != len) return None
    }
    Some(out)
  }

  private def parseIPv6(text: String): Option[Array[Byte]] =
    if (text.indexOf('.') >= 0) parseIPv6WithEmbeddedIPv4(text)
    else parsePureIPv6(text)

  private def parsePureIPv6(text: String): Option[Array[Byte]] = {
    val comp = text.indexOf("::")
    if (comp >= 0) {
      if (text.lastIndexOf("::") != comp) return None
      val leftGroups  = new Array[Int](8)
      val rightGroups = new Array[Int](8)
      val leftCount   = parseV6Side(text, 0, comp, leftGroups, 0)
      if (leftCount < 0) return None
      val rightCount  = parseV6Side(text, comp + 2, text.length, rightGroups, 0)
      if (rightCount < 0) return None
      if (leftCount + rightCount > 7) return None
      val groups      = new Array[Int](8)
      var i           = 0
      while (i < leftCount) {
        groups(i) = leftGroups(i)
        i += 1
      }
      var z           = leftCount
      while (z < 8 - rightCount) {
        groups(z) = 0
        z += 1
      }
      var j           = 0
      while (j < rightCount) {
        groups(8 - rightCount + j) = rightGroups(j)
        j += 1
      }
      Some(groupsToBytes(groups))
    } else {
      val groups = new Array[Int](8)
      val count  = parseV6Side(text, 0, text.length, groups, 0)
      if (count != 8) None
      else Some(groupsToBytes(groups))
    }
  }

  private def parseIPv6WithEmbeddedIPv4(text: String): Option[Array[Byte]] = {
    val lastColon  = text.lastIndexOf(':')
    if (lastColon < 0) return None
    val tailStart  = lastColon + 1
    val tail       = text.substring(tailStart)
    if (tail.indexOf('.') < 0) return None
    val v4opt      = parseIPv4(tail)
    if (v4opt.isEmpty) return None
    val v4bytes    = v4opt.get
    val rawHeadLen = text.length - tail.length
    if (rawHeadLen <= 0 || text.charAt(rawHeadLen - 1) != ':') return None
    val head       =
      if (rawHeadLen >= 2 && text.charAt(rawHeadLen - 2) == ':') text.substring(0, rawHeadLen)
      else text.substring(0, rawHeadLen - 1)
    if (head.isEmpty || head.indexOf('.') >= 0) return None
    val v4High     = ((v4bytes(0) & 0xff) << 8) | (v4bytes(1) & 0xff)
    val v4Low      = ((v4bytes(2) & 0xff) << 8) | (v4bytes(3) & 0xff)
    val groups     = new Array[Int](8)
    val comp       = head.indexOf("::")
    if (comp >= 0) {
      if (head.lastIndexOf("::") != comp) return None
      val leftGroups  = new Array[Int](8)
      val rightGroups = new Array[Int](8)
      val leftCount   = parseV6Side(head, 0, comp, leftGroups, 0)
      if (leftCount < 0) return None
      val rightCount  = parseV6Side(head, comp + 2, head.length, rightGroups, 0)
      if (rightCount < 0) return None
      if (leftCount + rightCount > 5) return None
      var i           = 0
      while (i < leftCount) {
        groups(i) = leftGroups(i)
        i += 1
      }
      var z           = leftCount
      while (z < 6 - rightCount) {
        groups(z) = 0
        z += 1
      }
      var j           = 0
      while (j < rightCount) {
        groups(6 - rightCount + j) = rightGroups(j)
        j += 1
      }
      groups(6) = v4High
      groups(7) = v4Low
    } else {
      val headCount = parseV6Side(head, 0, head.length, groups, 0)
      if (headCount != 6) return None
      groups(6) = v4High
      groups(7) = v4Low
    }
    val bytes      = groupsToBytes(groups)
    var i          = 0
    var isMapped   = true
    while (i < 10 && isMapped) {
      if (bytes(i) != 0) isMapped = false
      i += 1
    }
    if (isMapped && ((bytes(10) & 0xff) != 0xff || (bytes(11) & 0xff) != 0xff)) isMapped = false
    if (isMapped) {
      val out = new Array[Byte](4)
      out(0) = bytes(12)
      out(1) = bytes(13)
      out(2) = bytes(14)
      out(3) = bytes(15)
      Some(out)
    } else Some(bytes)
  }

  private def parseV6Side(text: String, start: Int, end: Int, out: Array[Int], offset: Int): Int = {
    if (start == end) return 0
    if (start > end) return -1
    var pos   = start
    var count = 0
    while (pos < end) {
      var colon = pos
      while (colon < end && text.charAt(colon) != ':') colon += 1
      val glen  = colon - pos
      if (glen == 0 || glen > 4) return -1
      var value = 0
      var k     = pos
      while (k < colon) {
        val c = text.charAt(k)
        val d =
          if (c >= '0' && c <= '9') c - '0'
          else if (c >= 'a' && c <= 'f') c - 'a' + 10
          else if (c >= 'A' && c <= 'F') c - 'A' + 10
          else return -1
        value = (value << 4) | d
        k += 1
      }
      if (count >= 8) return -1
      out(offset + count) = value
      count += 1
      if (colon == end) pos = end
      else {
        pos = colon + 1
        if (pos == end) return -1
      }
    }
    count
  }

  private def groupsToBytes(groups: Array[Int]): Array[Byte] = {
    val out = new Array[Byte](16)
    var i   = 0
    while (i < 8) {
      out(i * 2) = ((groups(i) >> 8) & 0xff).toByte
      out(i * 2 + 1) = (groups(i) & 0xff).toByte
      i += 1
    }
    out
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
