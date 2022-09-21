package zio.http.netty.client

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContext, SslContextBuilder}
import zio.http.ClientSSLConfig

import java.io.{FileInputStream, InputStream}
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

object ClientSSLConverter {
  private def trustStoreToSslContext(trustStoreStream: InputStream, trustStorePassword: String): SslContext = {
    val trustStore          = KeyStore.getInstance("JKS")
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

    trustStore.load(trustStoreStream, trustStorePassword.toCharArray)
    trustManagerFactory.init(trustStore)
    SslContextBuilder
      .forClient()
      .trustManager(trustManagerFactory)
      .build()
  }

  private def certToSslContext(certStream: InputStream): SslContext =
    SslContextBuilder
      .forClient()
      .trustManager(certStream)
      .build()

  def toNettySSLContext(sslConfig: ClientSSLConfig): SslContext = sslConfig match {
    case ClientSSLConfig.Default =>
      SslContextBuilder
        .forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build()

    case ClientSSLConfig.FromCertFile(certPath) =>
      val certStream = new FileInputStream(certPath)
      certToSslContext(certStream)

    case ClientSSLConfig.FromCertResource(certPath) =>
      val certStream = getClass.getClassLoader.getResourceAsStream(certPath)
      certToSslContext(certStream)

    case ClientSSLConfig.FromTrustStoreFile(trustStorePath, trustStorePassword) =>
      val trustStoreStream = new FileInputStream(trustStorePath)
      trustStoreToSslContext(trustStoreStream, trustStorePassword)

    case ClientSSLConfig.FromTrustStoreResource(trustStorePath, trustStorePassword) =>
      val trustStoreStream = getClass.getClassLoader.getResourceAsStream(trustStorePath)
      trustStoreToSslContext(trustStoreStream, trustStorePassword)
  }

}
