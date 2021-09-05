package zhttp.service.client

import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.ssl.ApplicationProtocolConfig.{
  Protocol,
  SelectedListenerFailureBehavior,
  SelectorFailureBehavior,
}
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{
  ApplicationProtocolConfig,
  ApplicationProtocolNames,
  SslContext,
  SslContextBuilder,
  SupportedCipherSuiteFilter,
}

case object ClientSSLHandler {
  sealed trait ClientSSLOptions
  object ClientSSLOptions {
    case object DefaultSSL                                    extends ClientSSLOptions
    final case class CustomSSL(sslContext: SslContextBuilder) extends ClientSSLOptions
  }
  def ssl(sslOption: ClientSSLOptions, enableHttp2: Boolean = false): SslContext =
    if (enableHttp2)
      sslOption match {
        case ClientSSLOptions.DefaultSSL            =>
          SslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
            .applicationProtocolConfig(
              new ApplicationProtocolConfig(
                Protocol.ALPN,
                SelectorFailureBehavior.NO_ADVERTISE,
                SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1,
              ),
            )
            .build()
        case ClientSSLOptions.CustomSSL(sslContext) =>
          sslContext
            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
            .applicationProtocolConfig(
              new ApplicationProtocolConfig(
                Protocol.ALPN,
                SelectorFailureBehavior.NO_ADVERTISE,
                SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1,
              ),
            )
            .build()
      }
    else
      sslOption match {
        case ClientSSLOptions.DefaultSSL            =>
          SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
        case ClientSSLOptions.CustomSSL(sslContext) => sslContext.build()
      }

}