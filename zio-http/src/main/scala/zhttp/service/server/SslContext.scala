package zhttp.service.server
import io.netty.handler.ssl.ApplicationProtocolConfig.{Protocol, SelectedListenerFailureBehavior, SelectorFailureBehavior}
import io.netty.handler.ssl.{ApplicationProtocolConfig, ApplicationProtocolNames, SslContextBuilder, SslProvider}
import io.netty.handler.ssl.util.SelfSignedCertificate

object SslContext {

  val ssc = new SelfSignedCertificate
  val sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).sslProvider(SslProvider.JDK)
  .applicationProtocolConfig(
    new ApplicationProtocolConfig(Protocol.ALPN, SelectorFailureBehavior.NO_ADVERTISE,
      SelectedListenerFailureBehavior.ACCEPT, ApplicationProtocolNames.HTTP_1_1))
    .build()
}
