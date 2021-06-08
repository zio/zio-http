package zhttp.service.client

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContext, SslContextBuilder}

case object ClientSSLHandler {
  sealed trait SslClientOptions
  object SslClientOptions {
    case object DefaultSSLClient                             extends SslClientOptions
    final case class CustomSslClient(sslContext: SslContext) extends SslClientOptions
  }
  def ssl(sslOption: SslClientOptions): SslContext = {
    sslOption match {
      case SslClientOptions.DefaultSSLClient            =>
        SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
      case SslClientOptions.CustomSslClient(sslContext) => sslContext
    }
  }
}
