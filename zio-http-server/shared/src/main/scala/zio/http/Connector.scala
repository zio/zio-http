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
) {
  if (maxRequestBodySize < 0L)
    throw new IllegalArgumentException("maxRequestBodySize must be non-negative")
}

object Connector {
  val default: Connector = Connector()

  /** Default request-body cap: 1 MiB per stream. */
  val DefaultMaxRequestBodySize: Long = 1024L * 1024L

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
)

object TlsConfig {
  implicit val schema: Schema[TlsConfig] = Schema.derived[TlsConfig]
}
