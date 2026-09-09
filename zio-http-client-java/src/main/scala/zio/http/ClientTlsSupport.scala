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
 *
 * Fail-fast (MINOR-4): every driver validates its structured TLS material at
 * construction via [[validateTlsMaterial]] - a bad path, an unparseable key, or
 * a wrong store password throws [[IllegalArgumentException]] naming the
 * file/kind expected instead of surfacing late on the first request. Password
 * hygiene: config passwords are `Option[String]` (stable API - the caller-owned
 * strings cannot be cleared); the derived `char[]` copies handed to the
 * keystore are zeroed in `finally` after use.
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

  /**
   * Fail-fast gate: loads and parses every trust/key source exactly as
   * [[buildContext]] will on first use, discarding the result. Drivers call
   * this at construction (skipped when a programmatic [[SSLContext]] override
   * bypasses structured config) so bad material throws here - with the file and
   * kind named - rather than on the first request. `None` is a no-op.
   */
  def validateTlsMaterial(tls: Option[ClientTlsConfig]): Unit =
    tls.foreach { cfg => buildContext(cfg); () }

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
    val keyStore =
      try KeyStore.getInstance(store.storeType)
      catch {
        case e: Exception =>
          throw new IllegalArgumentException(
            s"ClientTrustSource.TrustStore storeType '${store.storeType}' is not a known keystore type: " +
              e.getMessage,
            e,
          )
      }
    loadStoreFile(keyStore, store.path, store.password, "ClientTrustSource.TrustStore", store.storeType)
    val factory  = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)
    factory.getTrustManagers
  }

  /**
   * Loads a filesystem keystore, naming the file on every failure. The derived
   * password `char[]` is zeroed after use (the config-level `String` is
   * caller-owned and cannot be cleared - see the object Scaladoc).
   */
  private def loadStoreFile(
    keyStore: KeyStore,
    path: String,
    password: Option[String],
    sourceKind: String,
    storeType: String,
  ): Unit = {
    val input =
      try Files.newInputStream(Paths.get(path))
      catch {
        case missing: java.nio.file.NoSuchFileException  =>
          throw new IllegalArgumentException(
            s"$sourceKind path '$path' does not exist (expected a readable $storeType keystore file)",
            missing,
          )
        case badPath: java.nio.file.InvalidPathException =>
          throw new IllegalArgumentException(s"$sourceKind path '$path' is not a valid filesystem path", badPath)
        case io: java.io.IOException                     =>
          throw new IllegalArgumentException(
            s"$sourceKind path '$path' is not readable (expected a readable $storeType keystore file): " +
              io.getMessage,
            io,
          )
      }
    val chars = password.map(_.toCharArray).orNull
    try {
      try keyStore.load(input, chars)
      catch {
        case e: Exception =>
          throw new IllegalArgumentException(
            s"$sourceKind path '$path' failed to load as $storeType " +
              "(wrong store password? corrupt file? wrong storeType?): " + e.getMessage,
            e,
          )
      }
    } finally {
      clearChars(chars)
      try input.close()
      catch { case _: Throwable => () }
    }
  }

  /**
   * Zeroes a derived password copy after use; tolerates the no-password `null`.
   */
  private def clearChars(chars: Array[Char]): Unit =
    if (chars != null) java.util.Arrays.fill(chars, '\u0000')

  private def trustManagersForPem(bundle: ClientTrustSource.PemBundle): Array[javax.net.ssl.TrustManager] = {
    val certificates =
      try
        CertificateFactory
          .getInstance("X.509")
          .generateCertificates(new ByteArrayInputStream(bundle.certificate.getBytes(StandardCharsets.UTF_8)))
      catch {
        case e: Exception =>
          throw new IllegalArgumentException(
            "ClientTrustSource.PemBundle.certificate is not parseable as X.509 PEM " +
              "(expected one or more '-----BEGIN CERTIFICATE-----' blocks): " + e.getMessage,
            e,
          )
      }
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
    if (index == 0)
      throw new IllegalArgumentException(
        "ClientTrustSource.PemBundle contained no X.509 certificates " +
          "(expected one or more '-----BEGIN CERTIFICATE-----' blocks)",
      )
    val factory      = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)
    factory.getTrustManagers
  }

  private def keyManagersForStore(store: ClientKeySource.KeyStore): Array[javax.net.ssl.KeyManager] = {
    val keyStore    =
      try KeyStore.getInstance(store.storeType)
      catch {
        case e: Exception =>
          throw new IllegalArgumentException(
            s"ClientKeySource.KeyStore storeType '${store.storeType}' is not a known keystore type: " + e.getMessage,
            e,
          )
      }
    loadStoreFile(keyStore, store.path, store.password, "ClientKeySource.KeyStore", store.storeType)
    val keyPassword = store.keyPassword.orElse(store.password).map(_.toCharArray).orNull
    try {
      val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      factory.init(keyStore, keyPassword)
      factory.getKeyManagers
    } catch {
      case e: Exception =>
        throw new IllegalArgumentException(
          s"ClientKeySource.KeyStore path '${store.path}' holds no key recoverable with the given key password " +
            "(wrong keyPassword? no private-key entry?): " + e.getMessage,
          e,
        )
    } finally clearChars(keyPassword)
  }

  private def keyManagersForPem(pem: ClientKeySource.PemKey): Array[javax.net.ssl.KeyManager] = {
    val certificates =
      try
        CertificateFactory
          .getInstance("X.509")
          .generateCertificates(new ByteArrayInputStream(pem.certificateChain.getBytes(StandardCharsets.UTF_8)))
          .toArray(new Array[java.security.cert.Certificate](0))
      catch {
        case e: Exception =>
          throw new IllegalArgumentException(
            "ClientKeySource.PemKey.certificateChain is not parseable as X.509 PEM " +
              "(expected one or more '-----BEGIN CERTIFICATE-----' blocks): " + e.getMessage,
            e,
          )
      }
    if (certificates.isEmpty)
      throw new IllegalArgumentException(
        "ClientKeySource.PemKey.certificateChain contained no X.509 certificates " +
          "(expected one or more '-----BEGIN CERTIFICATE-----' blocks)",
      )
    val key          = parsePkcs8PrivateKey(pem.privateKey)
    val keyStore     = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(null, Array.emptyCharArray)
    keyStore.setKeyEntry("client-key", key, Array.emptyCharArray, certificates)
    val factory      = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore, Array.emptyCharArray)
    factory.getKeyManagers
  }

  /**
   * Detects traditional/encrypted key encodings the JDK cannot parse (no new
   * crypto deps - detect and message, never a new parser). Returns the
   * actionable error, or `None` when the PEM claims PKCS#8.
   */
  private def detectUnsupportedKeyEncoding(pem: String): Option[String] =
    if (pem.contains("RSA PRIVATE KEY"))
      Some(
        "ClientKeySource.PemKey.privateKey is PKCS#1 ('-----BEGIN RSA PRIVATE KEY-----'), which the JDK cannot " +
          "parse directly: convert to PKCS#8 with `openssl pkcs8 -topk8 -inform PEM -in rsa.pem -out pkcs8.pem " +
          "-nocrypt` and use the '-----BEGIN PRIVATE KEY-----' output",
      )
    else if (pem.contains("EC PRIVATE KEY"))
      Some(
        "ClientKeySource.PemKey.privateKey is SEC1/EC-traditional ('-----BEGIN EC PRIVATE KEY-----'), which the " +
          "JDK cannot parse directly: convert to PKCS#8 with `openssl pkcs8 -topk8 -inform PEM -in ec.pem -out " +
          "pkcs8.pem -nocrypt` and use the '-----BEGIN PRIVATE KEY-----' output",
      )
    else if (pem.contains("DSA PRIVATE KEY"))
      Some(
        "ClientKeySource.PemKey.privateKey is traditional DSA ('-----BEGIN DSA PRIVATE KEY-----'), which the JDK " +
          "cannot parse directly: convert to PKCS#8 with `openssl pkcs8 -topk8 -inform PEM -in dsa.pem -out " +
          "pkcs8.pem -nocrypt` and use the '-----BEGIN PRIVATE KEY-----' output",
      )
    else if (pem.contains("ENCRYPTED PRIVATE KEY"))
      Some(
        "ClientKeySource.PemKey.privateKey is password-protected ('-----BEGIN ENCRYPTED PRIVATE KEY-----'): " +
          "decrypt first, e.g. `openssl pkcs8 -in encrypted.pem -out clear.pem`, and use the PKCS#8 " +
          "'-----BEGIN PRIVATE KEY-----' output (encrypted keys are unsupported - key passwords have no config " +
          "surface)",
      )
    else if (pem.contains("OPENSSH PRIVATE KEY"))
      Some(
        "ClientKeySource.PemKey.privateKey is OpenSSH ('-----BEGIN OPENSSH PRIVATE KEY-----'), which the JDK " +
          "cannot parse directly: export PKCS#8 from the key owner tooling and use the '-----BEGIN PRIVATE " +
          "KEY-----' output",
      )
    else None

  private def parsePkcs8PrivateKey(pem: String): java.security.PrivateKey = {
    detectUnsupportedKeyEncoding(pem).foreach { message => throw new IllegalArgumentException(message) }
    val der  =
      try Base64.getDecoder.decode(pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""))
      catch {
        case e: IllegalArgumentException =>
          throw new IllegalArgumentException(
            "ClientKeySource.PemKey.privateKey is not valid Base64 PEM " +
              "(expected PKCS#8 '-----BEGIN PRIVATE KEY-----'): " + e.getMessage,
            e,
          )
      }
    val spec =
      try new PKCS8EncodedKeySpec(der)
      catch {
        case e: IllegalArgumentException =>
          throw new IllegalArgumentException(
            "ClientKeySource.PemKey.privateKey holds no PKCS#8 key bytes " +
              "(expected PKCS#8 '-----BEGIN PRIVATE KEY-----'): " + e.getMessage,
            e,
          )
      }
    List("RSA", "EC", "DSA", "Ed25519", "Ed448").iterator
      .map(algorithm => Try(KeyFactory.getInstance(algorithm).generatePrivate(spec)))
      .collectFirst { case scala.util.Success(privateKey) => privateKey }
      .getOrElse(
        throw new IllegalArgumentException(
          "Unable to parse ClientKeySource.PemKey.privateKey with RSA/EC/DSA/Ed25519/Ed448 " +
            "(expected PKCS#8 '-----BEGIN PRIVATE KEY-----'; traditional encodings are detected above with " +
            "conversion hints)",
        ),
      )
  }
}
