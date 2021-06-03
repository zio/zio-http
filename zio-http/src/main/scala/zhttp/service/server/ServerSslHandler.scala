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
  val keyStore: Option[KeyStore]       = Option(KeyStore.getInstance("JKS"))
  val keyStorePath: Option[String]     = Option(System.getProperty("javax.net.ssl.keyStore"))
  val keyStorePassword: Option[String] = Option(System.getProperty("javax.net.ssl.keyStorePassword"))
  val certPassword                     = Option(System.getProperty("javax.net.ssl.certPassword"))

  def getSslContext(
    keyStore: Option[KeyStore],
    keyStorePath: Option[String],
    keyStorePassword: Option[String],
    certPassword: Option[String],
  ): Option[SslContext] = {
    if (keyStore.isEmpty || keyStorePath.isEmpty || keyStorePassword.isEmpty || certPassword.isEmpty) None
    else {
      keyStore.get.load(new FileInputStream(keyStorePath.get), keyStorePassword.get.toCharArray)
      val kmf = KeyManagerFactory.getInstance("SunX509")
      kmf.init(keyStore.get, certPassword.get.toCharArray)
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

  val ssl: Option[SslContext] = getSslContext(keyStore, keyStorePath, keyStorePassword, certPassword)
}
