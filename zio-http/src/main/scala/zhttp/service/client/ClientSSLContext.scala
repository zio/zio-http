package zhttp.service.client

import io.netty.handler.ssl.{SslContext, SslContextBuilder}
import zhttp.http.TrustStoreConfig

import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import scala.util.Try

case object ClientSSLContext {
  def getSSLContext(trustStoreConfig: TrustStoreConfig): SslContext = {
    val trustStorePath      = trustStoreConfig.trustStorePath
    val trustStorePassword  = trustStoreConfig.trustStorePassword
    val trustStoreAlgorithm = trustStoreConfig.trustStoreAlgorithm

    if (trustStorePath == "" || trustStorePassword == "")
      SslContextBuilder.forClient().build()
    else {
      val trustStore: KeyStore                     = KeyStore.getInstance("JKS")
      val trustManagerFactory: TrustManagerFactory = TrustManagerFactory.getInstance(trustStoreAlgorithm)
      val trustStoreFile: Try[FileInputStream]     = Try(new FileInputStream(trustStorePath))

      def tsLoad(trustStore: KeyStore): Either[Throwable, Unit] = Try(
        trustStore.load(trustStoreFile.get, trustStorePassword.toCharArray),
      ).toEither

      def buildSSLContext(trustStore: KeyStore): SslContext = {
        trustManagerFactory.init(trustStore)
        SslContextBuilder.forClient().trustManager(trustManagerFactory).build()
      }

      tsLoad(trustStore) match {
        case Left(exception) => throw exception             //provided file/password is not correct
        case Right(_)        => buildSSLContext(trustStore) // provided truststore file and password  is correct
      }

    }
  }
}
