package zhttp.service

import io.netty.handler.ssl._
import io.netty.handler.ssl.util._
import zio._

import java.security._
import javax.net.ssl._

object Ssl {
  def pkcs12KeyManagerFactory(
    resource: String,
    keystorePassword: String = "password",
    keyManagerPassword: String = "password",
  ): Task[KeyManagerFactory] =
    Task {
      val ksStream = this.getClass.getResourceAsStream(resource)
      val ks       = KeyStore.getInstance("PKCS12")
      ks.load(ksStream, keystorePassword.toCharArray)
      ksStream.close()

      val kmf = KeyManagerFactory.getInstance(
        Option(Security.getProperty("ssl.KeyManagerFactory.algorithm")).getOrElse(KeyManagerFactory.getDefaultAlgorithm),
      )

      kmf.init(ks, keyManagerPassword.toCharArray)

      kmf
    }

  def keyManagerFactory(
    resource: String,
    keystorePassword: String = "password",
    keyManagerPassword: String = "password",
  ): Task[KeyManagerFactory] =
    Task {
      val ksStream = this.getClass.getResourceAsStream(resource)
      val ks       = KeyStore.getInstance("JKS")
      ks.load(ksStream, keystorePassword.toCharArray)
      ksStream.close()

      val kmf = KeyManagerFactory.getInstance(
        Option(Security.getProperty("ssl.KeyManagerFactory.algorithm")).getOrElse(KeyManagerFactory.getDefaultAlgorithm),
      )

      kmf.init(ks, keyManagerPassword.toCharArray)

      kmf
    }

  lazy val clientContext: Task[SslContext] =
    pkcs12KeyManagerFactory("/badssl.com-client.p12", "badssl.com", "badssl.com") as
      SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()

  lazy val serverContext: Task[SslContext] =
    keyManagerFactory("/server.jks").map(kmf => SslContextBuilder.forServer(kmf).build())
}
