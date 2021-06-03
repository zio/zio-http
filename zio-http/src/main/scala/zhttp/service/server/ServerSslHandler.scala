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
  val keyStore: KeyStore       = KeyStore.getInstance("JKS")
  val keyStorePath: String     = System.getProperty("javax.net.ssl.keyStore")
  val keyStorePassword: String = System.getProperty("javax.net.ssl.keyStorePassword")
  val certPassword: String     = System.getProperty("javax.net.ssl.certPassword")

  def getSslContext(
    keyStore: KeyStore,
    keyStorePath: String,
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

  val ssl: Option[SslContext] = getSslContext(keyStore, keyStorePath, keyStorePassword, certPassword)
}
