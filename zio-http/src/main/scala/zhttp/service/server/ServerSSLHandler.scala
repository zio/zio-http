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

import java.io.{File, InputStream}
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

object ServerSSLHandler {

  case class ServerSSLOptions(sslContext: SslContext, httpBehaviour: SSLHttpBehaviour = SSLHttpBehaviour.Redirect)

  sealed trait SSLHttpBehaviour

  object SSLHttpBehaviour {

    case object Redirect extends SSLHttpBehaviour

    case object Accept extends SSLHttpBehaviour

    case object Fail extends SSLHttpBehaviour

  }

  def ctxSelfSigned(): SslContext = {
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

  def ctxFromKeystore(
    keyStoreInputStream: InputStream,
    keyStorePassword: String,
    certPassword: String,
  ): SslContext = {
    val keyStore: KeyStore = KeyStore.getInstance("JKS")
    keyStore.load(keyStoreInputStream, keyStorePassword.toCharArray)
    val kmf                = KeyManagerFactory.getInstance("SunX509")
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

  def ctxFromCert(certFile: File, keyFile: File): SslContext = {
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
  }
}
