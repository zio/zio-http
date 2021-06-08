package zhttp.service.client

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContext, SslContextBuilder}

case object ClientSSLHandler {
  sealed trait ClientSSLOptions
  object ClientSSLOptions {
    case object DefaultSSL                             extends ClientSSLOptions
    final case class CustomSSL(sslContext: SslContext) extends ClientSSLOptions
  }
  def ssl(sslOption: ClientSSLOptions): SslContext = {
    sslOption match {
      case ClientSSLOptions.DefaultSSL            =>
        SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
      case ClientSSLOptions.CustomSSL(sslContext) => sslContext
    }
  }
}
