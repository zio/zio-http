package zio.http

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException

import scala.annotation.experimental
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/**
 * JDK `HttpClient`-backed [[Client]].
 *
 * Protocol version and TLS behavior are policy-driven (T14): the ALPN offer
 * follows [[ClientConfig.alpn]] (`H2PreferredWithH11Fallback` offers
 * `h2`+`http/1.1`; `StrictH2`/`H2OnlyH2C` offer `h2` only), trust/key material
 * plus pinned versions come from [[ClientTlsConfig]] via [[ClientTlsSupport]],
 * and a post-response gate turns any server-driven downgrade under a strict
 * policy into an [[SSLHandshakeException]] instead of a silent 200.
 * [[JavaH2Client.h11]] forces the HTTP/1.1 leg - it is the composable fallback
 * [[LoomH2ClientDriver]] delegates to, not a second client implementation.
 */
class JavaH2Client(
  httpClient0: => HttpClient,
  config: ClientConfig,
  enforceAlpn: Boolean = true,
) extends Client {

  /**
   * The JDK client is built on first `send`, not at construction: wiring a
   * config must never touch the network. Structured TLS material is still
   * validated at construction (see the `apply`/`h11` factories): a bad path,
   * key, or password fails fast here, not on first use.
   */
  private lazy val httpClient: HttpClient = httpClient0

  def send(request: Request): Response = {
    val javaRequest  = toJavaRequest(request)
    val javaResponse = httpClient.send(javaRequest, JavaH2Client.boundedByteArrayHandler(config.maxResponseBodySize))

    val response = toResponse(javaResponse)
    enforcePolicy(response.version)
    response
  }

  /**
   * Post-response policy gate: the JDK negotiates on our behalf, so a strict
   * policy downgraded to HTTP/1.1 by the server must fail here - loudly -
   * instead of returning a silent 200. Forced-h1.1 legs (`h11`,
   * `enforceAlpn = false`) skip this by construction.
   */
  private def enforcePolicy(version: Version): Unit =
    if (enforceAlpn) {
      val strict = config.alpn == ClientAlpnPolicy.StrictH2 || config.alpn == ClientAlpnPolicy.H2OnlyH2C
      if (strict && version != Version.`HTTP/2.0`)
        throw new SSLHandshakeException(
          s"ClientAlpnPolicy ${config.alpn} forbids the negotiated protocol '$version'",
        )
    }

  private def toJavaRequest(request: Request): HttpRequest = {
    val builder = HttpRequest
      .newBuilder(toUri(request.url))
      .timeout(config.effectiveRequestTimeout)
      .method(request.method.name, toBodyPublisher(request.body))

    val headerPairs = request.headers.toList
    var i           = 0
    while (i < headerPairs.length) {
      val (name, value) = headerPairs(i)
      builder.header(name, value)
      i += 1
    }

    builder.build()
  }

  private def toResponse(javaResponse: HttpResponse[Array[Byte]]): Response = {
    val headers     = toHeaders(javaResponse.headers())
    val contentType = headers.get(Header.ContentType).map(_.value).getOrElse(ContentType.`application/octet-stream`)

    Response(
      status = Status.fromInt(javaResponse.statusCode()),
      headers = headers,
      body = Body.fromArray(javaResponse.body(), contentType),
      version = toVersion(javaResponse.version()),
    )
  }

  private def toUri(url: URL): URI =
    if (url.isAbsolute) URI.create(url.encode)
    else throw new IllegalArgumentException("JavaH2Client requires absolute request URLs")

  private def toBodyPublisher(body: Body): HttpRequest.BodyPublisher =
    if (body.isEmpty) HttpRequest.BodyPublishers.noBody()
    else HttpRequest.BodyPublishers.ofByteArray(body.toArray)

  private def toHeaders(httpHeaders: java.net.http.HttpHeaders): Headers = {
    val builder = HeadersBuilder.make()

    for {
      entry <- httpHeaders.map().entrySet().asScala
      // HTTP/2 responses surface pseudo-headers (`:status`, ...) in the JDK
      // header map; they are framing, not headers, and `Headers` rejects
      // `:` names - drop them instead of crashing the exchange.
      if !entry.getKey.startsWith(":")
      value <- entry.getValue.asScala
    } builder.add(entry.getKey, value)

    builder.build()
  }

  private def toVersion(version: HttpClient.Version): Version = version match {
    case HttpClient.Version.HTTP_2   => Version.`HTTP/2.0`
    case HttpClient.Version.HTTP_1_1 => Version.`HTTP/1.1`
  }
}

object JavaH2Client {

  /**
   * Default response-body cap, sourced from [[ClientConfig]] so every client
   * leg enforces the same bound. Raise via
   * `ClientConfig(maxResponseBodySize = ...)`; the default preserves existing
   * call sites.
   */
  val DefaultMaxResponseBodySize: Long = ClientConfig.DefaultMaxResponseBodySize

  def default: JavaH2Client = apply(ClientConfig())

  def apply(config: ClientConfig): JavaH2Client = {
    ClientTlsSupport.validateTlsMaterial(config.tls)
    new JavaH2Client(configuredHttpClient(config, None, selectedVersion(config)), config)
  }

  def apply(config: ClientConfig, sslContext: SSLContext): JavaH2Client =
    new JavaH2Client(configuredHttpClient(config, Some(sslContext), selectedVersion(config)), config)

  /**
   * Forced HTTP/1.1 leg for [[LoomH2ClientDriver]]'s composable fallback: same
   * mapping, same timeouts, ALPN pinned to `http/1.1`.
   */
  @experimental
  def h11(config: ClientConfig): JavaH2Client = {
    ClientTlsSupport.validateTlsMaterial(config.tls)
    new JavaH2Client(configuredHttpClient(config, None, HttpClient.Version.HTTP_1_1), config, enforceAlpn = false)
  }

  @experimental
  def h11(config: ClientConfig, sslContext: SSLContext): JavaH2Client =
    new JavaH2Client(
      configuredHttpClient(config, Some(sslContext), HttpClient.Version.HTTP_1_1),
      config,
      enforceAlpn = false,
    )

  /**
   * Policy-driven version selection replacing the old hardcoded HTTP_2: every
   * policy negotiates through HTTP_2 (the JDK itself drops to HTTP/1.1 when the
   * server only offers `http/1.1`); the ALPN offer and the post-response
   * [[enforcePolicy]] gate carry the policy semantics, so a strict-policy
   * downgrade surfaces as [[SSLHandshakeException]], never a silent 200.
   */
  private def selectedVersion(config: ClientConfig): HttpClient.Version =
    HttpClient.Version.HTTP_2

  /**
   * Response-body handler that accounts bytes incrementally and fails fast past
   * `maxBytes`: the subscription is cancelled the moment the cap is exceeded,
   * so a malicious server cannot force unbounded `ofByteArray` buffering on the
   * client.
   */
  private[http] def boundedByteArrayHandler(
    maxBytes: Long = DefaultMaxResponseBodySize,
  ): HttpResponse.BodyHandler[Array[Byte]] =
    new HttpResponse.BodyHandler[Array[Byte]] {
      override def apply(responseInfo: HttpResponse.ResponseInfo): HttpResponse.BodySubscriber[Array[Byte]] =
        new BoundedByteArraySubscriber(maxBytes)
    }

  private final class BoundedByteArraySubscriber(maxBytes: Long) extends HttpResponse.BodySubscriber[Array[Byte]] {
    private val buffer                          = new ByteArrayOutputStream()
    private val result                          = new CompletableFuture[Array[Byte]]()
    private var subscription: Flow.Subscription = null
    private var total: Long                     = 0L

    override def onSubscribe(s: Flow.Subscription): Unit = {
      subscription = s
      s.request(Long.MaxValue)
    }

    override def onNext(items: java.util.List[ByteBuffer]): Unit =
      try {
        val iterator = items.iterator()
        while (iterator.hasNext) {
          val chunk = iterator.next()
          val size  = chunk.remaining().toLong
          if (total + size > maxBytes) {
            val s = subscription
            if (s != null) s.cancel()
            result.completeExceptionally(ResponseBodyTooLarge(maxBytes))
            return
          }
          val bytes = new Array[Byte](chunk.remaining())
          chunk.get(bytes)
          buffer.write(bytes, 0, bytes.length)
          total += size
        }
      } catch {
        case NonFatal(error) =>
          val s = subscription
          if (s != null) s.cancel()
          result.completeExceptionally(error)
      }

    override def onError(throwable: Throwable): Unit =
      result.completeExceptionally(throwable)

    override def onComplete(): Unit =
      result.complete(buffer.toByteArray)

    override def getBody: CompletionStage[Array[Byte]] = result
  }

  private def configuredHttpClient(
    config: ClientConfig,
    sslContextOverride: Option[SSLContext],
    version: HttpClient.Version,
  ): HttpClient = {
    val context = ClientTlsSupport.resolveContext(config.tls, sslContextOverride)
    val builder = HttpClient
      .newBuilder()
      .version(version)
      .connectTimeout(config.effectiveConnectTimeout)
      .followRedirects(if (config.followRedirects) HttpClient.Redirect.NORMAL else HttpClient.Redirect.NEVER)
      .sslContext(context)
    val params  = ClientTlsSupport.alpnParameters(offerProtocols(config, version), config.tls, context)
    builder.sslParameters(params).build()
  }

  /**
   * The ALPN offer actually placed on the wire: forced-h1.1 legs offer only
   * `http/1.1`; everything else follows the configured policy.
   */
  private def offerProtocols(config: ClientConfig, version: HttpClient.Version): List[String] =
    if (version == HttpClient.Version.HTTP_1_1) List("http/1.1")
    else config.alpn.alpnProtocols
}
