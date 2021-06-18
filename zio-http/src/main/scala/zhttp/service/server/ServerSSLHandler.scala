package zhttp.service.server

import io.netty.handler.ssl.ApplicationProtocolConfig.{
  Protocol,
  SelectedListenerFailureBehavior,
  SelectorFailureBehavior,
}
import io.netty.handler.ssl._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

object ServerSSLHandler {

  sealed trait ServerSSLOptions

  object ServerSSLOptions {

    case object NoSSL                                           extends ServerSSLOptions
    case object SelfSigned                                      extends ServerSSLOptions
    final case class SSLFromKeystore(
      keyStoreInputStream: ByteArrayOutputStream,
      keyStorePassword: String,
      certPassword: String,
    )                                                           extends ServerSSLOptions
    final case class SSLFromCert(certFile: File, keyFile: File) extends ServerSSLOptions
    final case class CustomSSL(sslContext: SslContext)          extends ServerSSLOptions

  }

  sealed trait SSLHttpBehaviour

  object SSLHttpBehaviour {
    case object Redirect extends SSLHttpBehaviour
    case object Accept   extends SSLHttpBehaviour
  }

  def ssl(sslOption: ServerSSLOptions): SslContext = {
    sslOption match {
      case ServerSSLOptions.NoSSL                                                                          => null
      case ServerSSLOptions.SelfSigned                                                                     => {
        import io.netty.handler.ssl.util.SelfSignedCertificate
        val ssc = new SelfSignedCertificate
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
          .build()
      }
      case ServerSSLOptions.SSLFromKeystore(keyStoreByteArrayOutputStream, keyStorePassword, certPassword) => {
        val keyStore: KeyStore  = KeyStore.getInstance("JKS")
        val keyStoreInputStream = new ByteArrayInputStream(keyStoreByteArrayOutputStream.toByteArray)
        keyStore.load(keyStoreInputStream, keyStorePassword.toCharArray)
        val kmf                 = KeyManagerFactory.getInstance("SunX509")
        kmf.init(keyStore, certPassword.toCharArray)
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
          .build()
      }
      case ServerSSLOptions.SSLFromCert(certFile, keyFile)                                                 =>
        SslContextBuilder
          .forServer(certFile, keyFile)
          .sslProvider(SslProvider.JDK)
          .applicationProtocolConfig(
            new ApplicationProtocolConfig(
              Protocol.ALPN,
              SelectorFailureBehavior.NO_ADVERTISE,
              SelectedListenerFailureBehavior.ACCEPT,
              ApplicationProtocolNames.HTTP_1_1,
            ),
          )
          .build()
      case ServerSSLOptions.CustomSSL(sslContext)                                                          => sslContext
    }

  }
}
