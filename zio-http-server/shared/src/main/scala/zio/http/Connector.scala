package zio.http

import java.nio.file.Path

import zio.blocks.config.Secret

case class Connector(
  bind: BindAddress = BindAddress.Tcp(),
  protocol: Protocol = Protocol.H2C(),
  idleTimeout: java.time.Duration = java.time.Duration.ofSeconds(60),
)

object Connector {
  val default: Connector = Connector()
}

sealed trait BindAddress
object BindAddress {
  case class Tcp(host: String = "0.0.0.0", port: Int = 8080) extends BindAddress
  case class Unix(path: Path) extends BindAddress

  def localhost(port: Int): BindAddress = Tcp("127.0.0.1", port)
  def anyHost(port: Int): BindAddress = Tcp("0.0.0.0", port)
}

sealed trait Protocol
object Protocol {
  case class H2C(http2: Http2Config = Http2Config()) extends Protocol
  case class H2(tls: TlsConfig, http2: Http2Config = Http2Config()) extends Protocol
  case class H3(tls: TlsConfig, quic: QuicConfig = QuicConfig(), http3: Http3Config = Http3Config()) extends Protocol
}

case class Http2Config(
  maxConcurrentStreams: Int = 100,
  initialWindowSize: Int = 65535,
  maxFrameSize: Int = 16384,
  maxHeaderListSize: Int = 8192,
)

case class Http3Config(
  maxFieldSectionSize: Long = 8192,
  qpackMaxTableCapacity: Int = 4096,
  qpackBlockedStreams: Int = 100,
)

case class QuicConfig(
  maxIdleTimeout: java.time.Duration = java.time.Duration.ofSeconds(30),
)

sealed trait TlsSource
object TlsSource {
  case class FilePath(path: Path) extends TlsSource
  case class PemString(pem: Secret) extends TlsSource
  case class SslContext(ctx: javax.net.ssl.SSLContext) extends TlsSource
}

case class TlsConfig(
  certChain: TlsSource,
  privateKey: TlsSource,
)
