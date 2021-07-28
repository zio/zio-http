package zhttp.service.client

import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.ssl.ApplicationProtocolConfig.{
  Protocol,
  SelectedListenerFailureBehavior,
  SelectorFailureBehavior,
}
import io.netty.handler.ssl.util.{InsecureTrustManagerFactory => JInsecureTrustManagerFactory}
import io.netty.handler.ssl.{
  ApplicationProtocolConfig,
  ApplicationProtocolNames,
  SslContext => JSslContext,
  SslContextBuilder => JSslContextBuilder,
  SupportedCipherSuiteFilter,
}

case object ClientSSLHandler {
  sealed trait ClientSSLOptions
  object ClientSSLOptions {
    case object DefaultSSL                                     extends ClientSSLOptions
    final case class CustomSSL(sslContext: JSslContextBuilder) extends ClientSSLOptions
  }
  def ssl(sslOption: ClientSSLOptions, http2: Boolean): JSslContext = {
    val builder = sslOption match {
      case ClientSSLOptions.DefaultSSL            =>
        JSslContextBuilder.forClient().trustManager(JInsecureTrustManagerFactory.INSTANCE)
      case ClientSSLOptions.CustomSSL(sslContext) => sslContext
    }
    if (http2)
      builder
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
        .build();
    else
      builder
        .applicationProtocolConfig(
          new ApplicationProtocolConfig(
            Protocol.ALPN,
            SelectorFailureBehavior.NO_ADVERTISE,
            SelectedListenerFailureBehavior.ACCEPT,
            ApplicationProtocolNames.HTTP_1_1,
          ),
        )
        .build();
  }
}
