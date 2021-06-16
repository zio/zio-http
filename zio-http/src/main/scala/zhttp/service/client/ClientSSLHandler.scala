package zhttp.service.client

import io.netty.handler.ssl.util.{InsecureTrustManagerFactory => JInsecureTrustManagerFactory}
import io.netty.handler.ssl.{SslContext => JSslContext, SslContextBuilder => JSslContextBuilder}

case object ClientSSLHandler {
  sealed trait ClientSSLOptions
  object ClientSSLOptions {
    case object DefaultSSL                              extends ClientSSLOptions
    final case class CustomSSL(sslContext: JSslContext) extends ClientSSLOptions
  }
  def ssl(sslOption: ClientSSLOptions): JSslContext = {
    sslOption match {
      case ClientSSLOptions.DefaultSSL            =>
        JSslContextBuilder.forClient().trustManager(JInsecureTrustManagerFactory.INSTANCE).build()
      case ClientSSLOptions.CustomSSL(sslContext) => sslContext
    }
  }
}
