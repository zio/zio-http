package zio.http

sealed trait ClientSSLConfig

object ClientSSLConfig {
  case object Default                                                                         extends ClientSSLConfig
  final case class FromCertFile(certPath: String)                                             extends ClientSSLConfig
  final case class FromCertResource(certPath: String)                                         extends ClientSSLConfig
  final case class FromTrustStoreResource(trustStorePath: String, trustStorePassword: String) extends ClientSSLConfig
  final case class FromTrustStoreFile(trustStorePath: String, trustStorePassword: String)     extends ClientSSLConfig
}
