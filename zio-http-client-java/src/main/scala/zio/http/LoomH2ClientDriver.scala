package zio.http

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket

import scala.annotation.experimental
import scala.util.control.NonFatal

/**
 * Loom (virtual-thread, blocking) HTTP client driver with policy-driven
 * protocol selection.
 *
 * Routing per [[ClientAlpnPolicy]] (read via [[ClientAlpnPolicy.alpnProtocols]] -
 * the policy is never reshaped here):
 *   - `http` URLs use H2C prior-knowledge over raw frames via [[H2WireClient]]
 *     (h2-codec `FrameCodec`/`Hpack`, never forked). Under
 *     [[ClientAlpnPolicy.H2PreferredWithH11Fallback]] a failed H2C attempt
 *     falls back to the stock JDK HTTP/1.1 leg; under
 *     [[ClientAlpnPolicy.StrictH2]] / [[ClientAlpnPolicy.H2OnlyH2C]] cleartext
 *     is H2C-only and failures propagate.
 *   - `https` URLs do a TLS handshake offering the policy's ALPN list, with
 *     trust/key material and pinned versions from [[ClientTlsConfig]]. A
 *     negotiated `h2` continues over raw frames; a negotiated `http/1.1`
 *     delegates to the JDK h1.1 leg under H2PreferredWithH11Fallback
 *     (composable fallback - no second client implementation) and fails fast
 *     with [[SSLHandshakeException]] under StrictH2/H2OnlyH2C. Any other (or
 *     empty) negotiated protocol fails fast under every policy - unknown
 *     protocols are never defaulted silently.
 *
 * Timeouts come from `ClientConfig.effectiveConnectTimeout` /
 * `effectiveRequestTimeout` (connect + socket read deadlines). Pooling is T15:
 * this driver opens one connection per `send` (single-connection, no pool, no
 * retry, bodies fully buffered) - [[PoolConfig]] is read where applicable and
 * its pooling semantics are deferred, not redefined.
 *
 * T15 seams: per-`send` connections (pooling/multiplexing), fully-buffered
 * bodies (streaming upload), no hostname endpoint identification on the raw H2
 * leg (the JDK fallback leg verifies per JDK defaults).
 */
@experimental
class LoomH2ClientDriver(
  config: ClientConfig,
  sslContextOverride: Option[SSLContext],
) extends Client {

  // Fail fast (MINOR-4): structured TLS material is loaded and parsed here so
  // a bad path/key/password throws at construction. Skipped when a
  // programmatic SSLContext bypasses structured config entirely.
  if (sslContextOverride.isEmpty) ClientTlsSupport.validateTlsMaterial(config.tls)

  def send(request: Request): Response = {
    val url  = request.url
    if (!url.isAbsolute) throw new IllegalArgumentException("LoomH2ClientDriver requires absolute request URLs")
    val host = url.host.getOrElse(
      throw new IllegalArgumentException("LoomH2ClientDriver requires an absolute URL with a host"),
    )
    url.scheme.map(_.text).getOrElse("") match {
      case "http"  => sendH2c(host, url.port.getOrElse(80), request)
      case "https" => sendTls(host, url.port.getOrElse(443), request)
      case other   => throw new IllegalArgumentException(s"LoomH2ClientDriver supports http/https only, got '$other'")
    }
  }

  private def pseudoParts(request: Request, defaultPort: Int): (String, String, String) = {
    val url      = request.url
    val scheme   = url.scheme.map(_.text).getOrElse(throw new IllegalArgumentException("URL carries no scheme"))
    val host     = url.host.getOrElse(throw new IllegalArgumentException("URL carries no host"))
    val portPart = url.port.filter(_ != defaultPort).map(":" + _).getOrElse("")
    val pathPart = {
      val encoded = url.path.encode
      if (encoded.isEmpty) "/" else encoded
    }
    val query    = url.queryParams.encode
    val target   = if (query.nonEmpty) pathPart + "?" + query else pathPart
    (scheme, host + portPart, target)
  }

  /**
   * H2Preferred falls back to the JDK h1.1 leg when prior-knowledge H2C fails
   * (e.g. a cleartext h1.1-only server); StrictH2/H2OnlyH2C propagate with
   * policy context instead of falling back silently.
   */
  private def sendH2c(host: String, port: Int, request: Request): Response = {
    val socket = connectPlain(host, port)
    try {
      val (scheme, authority, target) = pseudoParts(request, 80)
      H2WireClient.execute(socket.getInputStream, socket.getOutputStream, request, scheme, authority, target)
    } catch {
      case NonFatal(failure) if config.alpn == ClientAlpnPolicy.H2PreferredWithH11Fallback =>
        closeQuietly(socket)
        h11Fallback().send(request)
      case NonFatal(failure)                                                               =>
        closeQuietly(socket)
        throw new IOException(s"H2C prior-knowledge exchange failed under ${config.alpn}: $failure")
    } finally {
      closeQuietly(socket)
    }
  }

  private def sendTls(host: String, port: Int, request: Request): Response = {
    val context = ClientTlsSupport.resolveContext(config.tls, sslContextOverride)
    val raw     = new Socket()
    raw.connect(new InetSocketAddress(host, port), connectTimeoutMillis())
    raw.setSoTimeout(requestTimeoutMillis())
    val socket  = context.getSocketFactory.createSocket(raw, host, port, true).asInstanceOf[SSLSocket]
    try {
      socket.setSSLParameters(ClientTlsSupport.alpnParameters(config.alpn.alpnProtocols, config.tls, context))
      socket.setUseClientMode(true)
      socket.startHandshake()
      socket.getApplicationProtocol match {
        case "h2"       =>
          try {
            val (scheme, authority, target) = pseudoParts(request, 443)
            H2WireClient.execute(socket.getInputStream, socket.getOutputStream, request, scheme, authority, target)
          } finally {
            closeQuietly(socket)
          }
        case "http/1.1" =>
          closeQuietly(socket)
          config.alpn match {
            case ClientAlpnPolicy.H2PreferredWithH11Fallback => h11Fallback().send(request)
            case policy                                      =>
              throw new SSLHandshakeException(
                s"ClientAlpnPolicy $policy forbids the negotiated protocol 'http/1.1' (server $host:$port)",
              )
          }
        case other      =>
          closeQuietly(socket)
          val want =
            if (config.alpn == ClientAlpnPolicy.H2PreferredWithH11Fallback) "'h2' or 'http/1.1'" else "'h2'"
          throw new SSLHandshakeException(
            s"Unexpected ALPN protocol '$other' negotiated with $host:$port under ${config.alpn}; expected $want",
          )
      }
    } catch {
      case fatal: Throwable =>
        closeQuietly(socket)
        throw fatal
    }
  }

  private def h11Fallback(): Client =
    sslContextOverride match {
      case Some(context) => JavaH2Client.h11(config, context)
      case None          => JavaH2Client.h11(config)
    }

  private def connectPlain(host: String, port: Int): Socket = {
    val socket = new Socket()
    socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis())
    socket.setSoTimeout(requestTimeoutMillis())
    socket
  }

  private def connectTimeoutMillis(): Int =
    clampMillis(config.effectiveConnectTimeout.toMillis)

  private def requestTimeoutMillis(): Int =
    clampMillis(config.effectiveRequestTimeout.toMillis)

  private def clampMillis(millis: Long): Int =
    math.min(math.max(millis, 1L), Int.MaxValue.toLong).toInt

  private def closeQuietly(socket: Socket): Unit =
    if (socket != null) {
      try socket.close()
      catch { case _: Throwable => () }
    }
}

@experimental
object LoomH2ClientDriver {

  def default: LoomH2ClientDriver = apply(ClientConfig())

  def apply(config: ClientConfig): LoomH2ClientDriver =
    new LoomH2ClientDriver(config, None)

  /**
   * Test/ops seam: a caller-provided [[SSLContext]] is wired programmatically
   * at construction time, outside structured config (mirrors the documented
   * [[ClientTlsConfig]] contract - trust-all test contexts never belong in
   * config).
   */
  def apply(config: ClientConfig, sslContext: SSLContext): LoomH2ClientDriver =
    new LoomH2ClientDriver(config, Some(sslContext))
}
