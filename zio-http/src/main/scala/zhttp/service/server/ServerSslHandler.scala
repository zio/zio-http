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

object ServerSslHandler {

  sealed trait SslServerOptions
  object SslServerOptions {
    case object NoSsl                                  extends SslServerOptions
    case object SelfSigned                             extends SslServerOptions
    final case class CustomSsl(sslContext: SslContext) extends SslServerOptions
  }

  def ssl(sslOption: SslServerOptions): Option[SslContext] = {
    sslOption match {
      case SslServerOptions.NoSsl                               => None
      case SslServerOptions.SelfSigned                          => {
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
      case SslServerOptions.CustomSsl(sslContext)               => Some(sslContext)
    }
  }
}
