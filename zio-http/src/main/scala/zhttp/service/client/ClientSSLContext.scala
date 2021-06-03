package zhttp.service.client

import java.io.{FileInputStream, IOException}
import java.security.KeyStore

import io.netty.handler.ssl.SslContext
import javax.net.ssl.TrustManagerFactory

import scala.util.Try

case object ClientSSLContext {
  def getSSLContext(): SslContext = {

    import io.netty.handler.ssl.SslContextBuilder
    // truststore
    val trustStore: Option[KeyStore]       = Option(KeyStore.getInstance("JKS"))
    val trustStorePath: Option[String]     = Option(System.getProperty("javax.net.ssl.trustStore"))
    val trustStorePassword: Option[String] = Option(System.getProperty("javax.net.ssl.trustStorePassword"))
    val trustManagerFactory                = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

    val trustStoreFile: Try[FileInputStream] = Try(new FileInputStream(trustStorePath.get))

    def tsLoad(trustStore: KeyStore): Either[Throwable, Unit] = Try(
      trustStore.load(trustStoreFile.get, trustStorePassword.get.toCharArray),
    ).toEither

    def buildSSLContext(trustStore: KeyStore): SslContext = {
      trustManagerFactory.init(trustStore)
      SslContextBuilder.forClient().trustManager(trustManagerFactory).build()
    }

    trustStore match {
      case Some(ts) =>
        tsLoad(ts) match {
          case Left(exception: IOException) => throw exception                       //provided file/password is not correct
          case Left(_)                      => SslContextBuilder.forClient().build() //java options not provided
          case Right(_)                     => buildSSLContext(ts)                   // provided truststore file and password  is correct
        }
      case None     => SslContextBuilder.forClient().build()
    }

  }
}
