package zhttp.service.server

import io.netty.handler.ssl.ApplicationProtocolConfig.{
  Protocol => JProtocol,
  SelectedListenerFailureBehavior => JSelectedListenerFailureBehavior,
  SelectorFailureBehavior => JSelectorFailureBehavior,
}
import io.netty.handler.ssl.{
  ApplicationProtocolConfig => JApplicationProtocolConfig,
  ApplicationProtocolNames => JApplicationProtocolNames,
  SslContext => JSslContext,
  SslContextBuilder => JSslContextBuilder,
  SslProvider => JSslProvider,
}

import java.io.{File, InputStream}
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

object ServerSSLHandler {

  case class ServerSSLOptions(sslContext: JSslContext, httpBehaviour: SSLHttpBehaviour = SSLHttpBehaviour.Redirect)

  sealed trait SSLHttpBehaviour

  object SSLHttpBehaviour {

    case object Redirect extends SSLHttpBehaviour

    case object Accept extends SSLHttpBehaviour

    case object Fail extends SSLHttpBehaviour

  }

  def ctxSelfSigned(): JSslContext = {
    import io.netty.handler.ssl.util.SelfSignedCertificate
    val ssc = new SelfSignedCertificate
    JSslContextBuilder
      .forServer(ssc.certificate(), ssc.privateKey())
      .sslProvider(JSslProvider.JDK)
      .applicationProtocolConfig(
        new JApplicationProtocolConfig(
          JProtocol.ALPN,
          JSelectorFailureBehavior.NO_ADVERTISE,
          JSelectedListenerFailureBehavior.ACCEPT,
          JApplicationProtocolNames.HTTP_1_1,
        ),
      )
      .build()
  }

  def ctxFromKeystore(
    keyStoreInputStream: InputStream,
    keyStorePassword: String,
    certPassword: String,
  ): JSslContext = {
    val keyStore: KeyStore = KeyStore.getInstance("JKS")
    keyStore.load(keyStoreInputStream, keyStorePassword.toCharArray)
    val kmf                = KeyManagerFactory.getInstance("SunX509")
    kmf.init(keyStore, certPassword.toCharArray)
    JSslContextBuilder
      .forServer(kmf)
      .sslProvider(JSslProvider.JDK)
      .applicationProtocolConfig(
        new JApplicationProtocolConfig(
          JProtocol.ALPN,
          JSelectorFailureBehavior.NO_ADVERTISE,
          JSelectedListenerFailureBehavior.ACCEPT,
          JApplicationProtocolNames.HTTP_1_1,
        ),
      )
      .build()
  }

  def ctxFromCert(certFile: File, keyFile: File): JSslContext = {
    JSslContextBuilder
      .forServer(certFile, keyFile)
      .sslProvider(JSslProvider.JDK)
      .applicationProtocolConfig(
        new JApplicationProtocolConfig(
          JProtocol.ALPN,
          JSelectorFailureBehavior.NO_ADVERTISE,
          JSelectedListenerFailureBehavior.ACCEPT,
          JApplicationProtocolNames.HTTP_1_1,
        ),
      )
      .build()
  }
}
