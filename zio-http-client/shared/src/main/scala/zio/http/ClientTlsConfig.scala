package zio.http

import zio.blocks.schema.Schema

/**
 * Where an HTTP client obtains TLS trust material.
 *
 * Deliberately JVM/JS-portable: filesystem locations stay `String` (drivers
 * resolve them with `Paths.get` on the JVM), inline PEM stays `String`, and
 * there is intentionally no `SSLContext` case - a caller-provided context is
 * wired programmatically at driver construction time, outside structured
 * config. This mirrors the *shape* of the server-side `TlsSource` without
 * importing server config into the client.
 */
sealed trait ClientTrustSource

object ClientTrustSource {

  /** Use the platform default trust store. This is the default. */
  case object SystemDefault extends ClientTrustSource

  /** Trust material loaded from a filesystem trust store. */
  final case class TrustStore(
    path: String,
    password: Option[String] = None,
    storeType: String = "PKCS12",
  ) extends ClientTrustSource

  /** Inline PEM trust material (certificate chain, no key required). */
  final case class PemBundle(
    certificate: String,
    privateKey: Option[String] = None,
  ) extends ClientTrustSource

  implicit val systemDefaultSchema: Schema[SystemDefault.type] = Schema.derived[SystemDefault.type]
  implicit val trustStoreSchema: Schema[TrustStore]            = Schema.derived[TrustStore]
  implicit val pemBundleSchema: Schema[PemBundle]              = Schema.derived[PemBundle]
  implicit val schema: Schema[ClientTrustSource]               = Schema.derived[ClientTrustSource]
}

/**
 * Where an HTTP client obtains its TLS client identity (mTLS key material).
 *
 * `None` (the [[ClientTlsConfig]] default) means no client certificate is
 * presented.
 */
sealed trait ClientKeySource

object ClientKeySource {

  /** Inline PEM identity: certificate chain plus private key. */
  final case class PemKey(
    certificateChain: String,
    privateKey: String,
  ) extends ClientKeySource

  /** Identity loaded from a filesystem key store. */
  final case class KeyStore(
    path: String,
    password: Option[String] = None,
    keyPassword: Option[String] = None,
    storeType: String = "PKCS12",
  ) extends ClientKeySource

  implicit val pemKeySchema: Schema[PemKey]     = Schema.derived[PemKey]
  implicit val keyStoreSchema: Schema[KeyStore] = Schema.derived[KeyStore]
  implicit val schema: Schema[ClientKeySource]  = Schema.derived[ClientKeySource]
}

/**
 * Client-side TLS configuration.
 *
 * Driver contract: a driver reads [[trust]] to build its trust managers,
 * [[key]] (when defined) to build its key managers for mTLS, and pins
 * [[tlsVersions]] on every TLS socket it creates (mirroring how `TcpListener`
 * pins versions per-socket downstream of either `SSLContext` source). An empty
 * [[tlsVersions]] fails fast - a client that negotiates no TLS version would
 * fail misleadingly late at handshake time.
 *
 * `None` (`ClientConfig.tls` default) means plaintext unless the URL scheme
 * says otherwise; drivers must not build TLS state for `None`.
 */
final case class ClientTlsConfig(
  trust: ClientTrustSource = ClientTrustSource.SystemDefault,
  key: Option[ClientKeySource] = None,
  tlsVersions: List[String] = ClientTlsConfig.DefaultTlsVersions,
) {
  if (tlsVersions.isEmpty)
    throw new IllegalArgumentException("ClientTlsConfig.tlsVersions must be non-empty")
}

object ClientTlsConfig {
  val DefaultTlsVersions: List[String] = List("TLSv1.3", "TLSv1.2")

  implicit val schema: Schema[ClientTlsConfig] = Schema.derived[ClientTlsConfig]
}
