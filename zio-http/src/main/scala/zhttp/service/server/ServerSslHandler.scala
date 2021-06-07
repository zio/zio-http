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

import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

object ServerSslHandler {

  sealed trait SslOptions
  object SslOptions {
    case object NoSsl                                  extends SslOptions
    case object SelfSigned                             extends SslOptions
    final case class DefaultCertificate(
      keyStorePath: String,
      keyStore: KeyStore = KeyStore.getInstance("JKS"),
      keyStorePassword: String = "123456",
      certPassword: String = "123456",
    )                                                  extends SslOptions
    final case class CustomSsl(sslContext: SslContext) extends SslOptions
  }

  def getSslContext(
    keyStorePath: String,
    keyStore: KeyStore,
    keyStorePassword: String,
    certPassword: String,
  ): Option[SslContext] = {
    if (keyStorePath == null || keyStorePassword == null || certPassword == null) None
    else {
      keyStore.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray)
      val kmf = KeyManagerFactory.getInstance("SunX509")
      kmf.init(keyStore, certPassword.toCharArray)
      Option(
        SslContextBuilder
          .forServer(kmf)
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
  }

  def ssl(sslOption: SslOptions): Option[SslContext] = {
    sslOption match {
      case SslOptions.NoSsl                               => None
      case SslOptions.SelfSigned                          => {
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
      case dc @ SslOptions.DefaultCertificate(_, _, _, _) =>
        getSslContext(dc.keyStorePath, dc.keyStore, dc.keyStorePassword, dc.certPassword)
      case SslOptions.CustomSsl(sslContext)               => Some(sslContext)
    }
  }
}
