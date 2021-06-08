package zhttp.http

import javax.net.ssl.TrustManagerFactory

final case class TrustStoreConfig(
  trustStorePath: String = "",
  trustStorePassword: String = "",
  trustStoreAlgorithm: String = TrustManagerFactory.getDefaultAlgorithm(),
)
