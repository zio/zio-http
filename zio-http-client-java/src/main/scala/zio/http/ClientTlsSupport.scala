package zio.http

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

import scala.util.Try

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.TrustManagerFactory

/**
 * Shared TLS plumbing for JVM client drivers (T14).
 *
 * Reads trust/key material and pinned versions from [[ClientTlsConfig]] exactly
 * once per construction site: [[resolveContext]] prefers a
 * programmatically-wired [[SSLContext]] override (test-only trust, or an
 * ops-provided context - never structured config), then builds from
 * `config.tls`, then falls back to the platform default. [[alpnParameters]]
 * always carries the [[ClientAlpnPolicy]] offer list so no driver negotiates a
 * protocol outside its policy silently.
 */
private[http] object ClientTlsSupport {

  def resolveContext(tls: Option[ClientTlsConfig], overrideContext: Option[SSLContext]): SSLContext =
    overrideContext.getOrElse(tls.map(buildContext).getOrElse(defaultContext()))

  /**
   * Builds socket parameters carrying the caller's ALPN offer list (always
   * explicit - no driver negotiates a protocol outside its policy silently).
   * `ClientAlpnPolicy` is sealed, so callers pass `policy.alpnProtocols` (or a
   * single-protocol override list) rather than subclassing it.
   */
  def alpnParameters(offer: List[String], tls: Option[ClientTlsConfig], context: SSLContext): SSLParameters = {
    val params = context.getDefaultSSLParameters
    params.setApplicationProtocols(offer.toArray)
    // HTTPS endpoint identification: the raw SSLSocket legs verify the server
    // cert hostname on every handshake (H2C cleartext has no TLS by
    // construction, so it is unaffected).
    params.setEndpointIdentificationAlgorithm("HTTPS")
    tls.foreach { cfg =>
      requireSupportedProtocols(context, cfg)
      params.setProtocols(cfg.tlsVersions.toArray)
    }
    params
  }

  def defaultContext(): SSLContext =
    SSLContext.getDefault

  private def buildContext(tls: ClientTlsConfig): SSLContext = {
    val trustManagers =
      tls.trust match {
        case ClientTrustSource.SystemDefault     => null
        case store: ClientTrustSource.TrustStore => trustManagersForStore(store)
        case bundle: ClientTrustSource.PemBundle => trustManagersForPem(bundle)
      }
    val keyManagers   =
      tls.key match {
        case None      => null
        case Some(key) =>
          key match {
            case pem: ClientKeySource.PemKey     => keyManagersForPem(pem)
            case store: ClientKeySource.KeyStore => keyManagersForStore(store)
          }
      }
    val context       = SSLContext.getInstance("TLS")
    context.init(keyManagers, trustManagers, new SecureRandom())
    requireSupportedProtocols(context, tls)
    context
  }

  private def requireSupportedProtocols(context: SSLContext, tls: ClientTlsConfig): Unit = {
    val supported = context.getSupportedSSLParameters.getProtocols.toSet
    val missing   = tls.tlsVersions.filterNot(supported.contains)
    if (missing.nonEmpty)
      throw new IllegalArgumentException(
        s"ClientTlsConfig.tlsVersions ${missing.mkString("[", ",", "]")} not supported by this JVM; " +
          s"supported: ${supported.toList.sorted.mkString("[", ",", "]")}",
      )
  }

  private def trustManagersForStore(store: ClientTrustSource.TrustStore): Array[javax.net.ssl.TrustManager] = {
    val keyStore = KeyStore.getInstance(store.storeType)
    val input    = Files.newInputStream(Paths.get(store.path))
    try keyStore.load(input, store.password.map(_.toCharArray).orNull)
    finally
      try input.close()
      catch { case _: Throwable => () }
    val factory  = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)
    factory.getTrustManagers
  }

  private def trustManagersForPem(bundle: ClientTrustSource.PemBundle): Array[javax.net.ssl.TrustManager] = {
    val certificates = CertificateFactory
      .getInstance("X.509")
      .generateCertificates(new ByteArrayInputStream(bundle.certificate.getBytes(StandardCharsets.UTF_8)))
    val keyStore     = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(null, Array.emptyCharArray)
    var index        = 0
    val it           = certificates.iterator()
    while (it.hasNext) {
      val cert = it.next()
      cert match {
        case x509: X509Certificate =>
          keyStore.setCertificateEntry("pem-" + index, x509)
          index += 1
        case _                     => ()
      }
    }
    if (index == 0) throw new IllegalArgumentException("ClientTrustSource.PemBundle contained no X.509 certificates")
    val factory      = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)
    factory.getTrustManagers
  }

  private def keyManagersForStore(store: ClientKeySource.KeyStore): Array[javax.net.ssl.KeyManager] = {
    val keyStore = KeyStore.getInstance(store.storeType)
    val input    = Files.newInputStream(Paths.get(store.path))
    try keyStore.load(input, store.password.map(_.toCharArray).orNull)
    finally
      try input.close()
      catch { case _: Throwable => () }
    val factory  = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore, store.keyPassword.orElse(store.password).map(_.toCharArray).orNull)
    factory.getKeyManagers
  }

  private def keyManagersForPem(pem: ClientKeySource.PemKey): Array[javax.net.ssl.KeyManager] = {
    val certificates = CertificateFactory
      .getInstance("X.509")
      .generateCertificates(new ByteArrayInputStream(pem.certificateChain.getBytes(StandardCharsets.UTF_8)))
      .toArray(new Array[java.security.cert.Certificate](0))
    if (certificates.isEmpty)
      throw new IllegalArgumentException("ClientKeySource.PemKey.certificateChain contained no X.509 certificates")
    val key          = parsePkcs8PrivateKey(pem.privateKey)
    val keyStore     = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(null, Array.emptyCharArray)
    keyStore.setKeyEntry("client-key", key, Array.emptyCharArray, certificates)
    val factory      = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore, Array.emptyCharArray)
    factory.getKeyManagers
  }

  private def parsePkcs8PrivateKey(pem: String): java.security.PrivateKey = {
    val der  = Base64.getDecoder.decode(pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""))
    val spec = new PKCS8EncodedKeySpec(der)
    List("RSA", "EC", "DSA", "Ed25519", "Ed448").iterator
      .map(algorithm => Try(KeyFactory.getInstance(algorithm).generatePrivate(spec)))
      .collectFirst { case scala.util.Success(privateKey) => privateKey }
      .getOrElse(throw new IllegalArgumentException("Unable to parse PEM private key with supported algorithms"))
  }
}
