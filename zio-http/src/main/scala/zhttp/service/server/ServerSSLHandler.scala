package zhttp.service.server

import io.netty.handler.codec.http2.{Http2SecurityUtil => JHttp2SecurityUtil}
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
  SupportedCipherSuiteFilter => JSupportedCipherSuiteFilter,
}

import java.io.InputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

object ServerSSLHandler {

  case class ServerSSLOptions(
    sslContext: JSslContextBuilder,
    httpBehaviour: SSLHttpBehaviour = SSLHttpBehaviour.Redirect,
  )

  sealed trait SSLHttpBehaviour

  object SSLHttpBehaviour {

    case object Redirect extends SSLHttpBehaviour

    case object Accept extends SSLHttpBehaviour

    case object Fail extends SSLHttpBehaviour

  }

  def ctxSelfSigned(): JSslContextBuilder = {
    import io.netty.handler.ssl.util.SelfSignedCertificate
    val ssc = new SelfSignedCertificate
    JSslContextBuilder
      .forServer(ssc.certificate(), ssc.privateKey())
      .sslProvider(JSslProvider.JDK)
  }

  def ctxFromKeystore(
    keyStoreInputStream: InputStream,
    keyStorePassword: String,
    certPassword: String,
  ): JSslContextBuilder = {
    val keyStore: KeyStore = KeyStore.getInstance("JKS")
    keyStore.load(keyStoreInputStream, keyStorePassword.toCharArray)
    val kmf                = KeyManagerFactory.getInstance("SunX509")
    kmf.init(keyStore, certPassword.toCharArray)
    JSslContextBuilder
      .forServer(kmf)
      .sslProvider(JSslProvider.JDK)
  }

  def ctxFromCert(cert: InputStream, key: InputStream): JSslContextBuilder = {
    JSslContextBuilder
      .forServer(cert, key)
      .sslProvider(JSslProvider.JDK)
  }

  def build(serverSSLOptions: ServerSSLOptions, enableHttp2: Boolean): JSslContext = {
    if (serverSSLOptions == null) null
    else {
      if (enableHttp2 == true) {
        serverSSLOptions.sslContext
          .ciphers(JHttp2SecurityUtil.CIPHERS, JSupportedCipherSuiteFilter.INSTANCE)
          .applicationProtocolConfig(
            new JApplicationProtocolConfig(
              JProtocol.ALPN,
              JSelectorFailureBehavior.NO_ADVERTISE,
              JSelectedListenerFailureBehavior.ACCEPT,
              JApplicationProtocolNames.HTTP_2,
              JApplicationProtocolNames.HTTP_1_1,
            ),
          )
          .build()
      } else {
        serverSSLOptions.sslContext
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
  }
}
