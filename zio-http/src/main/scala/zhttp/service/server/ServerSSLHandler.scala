package zhttp.service.server

import io.netty.handler.ssl.ApplicationProtocolConfig.{
  Protocol,
  SelectedListenerFailureBehavior,
  SelectorFailureBehavior,
}
import io.netty.handler.ssl.{
  ApplicationProtocolConfig,
  ApplicationProtocolNames,
  SslContext,
  SslContextBuilder,
  SslProvider,
}

object ServerSSLHandler {

  sealed trait ServerSSLOptions
  object ServerSSLOptions {
    case object NoSSL                                  extends ServerSSLOptions
    case object SelfSigned                             extends ServerSSLOptions
    final case class CustomSSL(sslContext: SslContext) extends ServerSSLOptions
  }

  def ssl(sslOption: ServerSSLOptions): Option[SslContext] = {
    sslOption match {
      case ServerSSLOptions.NoSSL                 => None
      case ServerSSLOptions.SelfSigned            => {
        import io.netty.handler.ssl.util.SelfSignedCertificate
        val ssc = new SelfSignedCertificate
        Option(
          SslContextBuilder
            .forServer(ssc.certificate(), ssc.privateKey())
            .sslProvider(SslProvider.JDK)
            .applicationProtocolConfig(
              new ApplicationProtocolConfig(
                Protocol.ALPN,
                SelectorFailureBehavior.NO_ADVERTISE,
                SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_1_1,
              ),
            )
            .build(),
        )
      }
      case ServerSSLOptions.CustomSSL(sslContext) => Some(sslContext)
    }
  }
}
