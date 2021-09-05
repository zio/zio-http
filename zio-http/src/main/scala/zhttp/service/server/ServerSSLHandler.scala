package zhttp.service.server

import io.netty.handler.ssl.ApplicationProtocolConfig.{Protocol, SelectedListenerFailureBehavior, SelectorFailureBehavior}
import java.io.InputStream
import java.security.KeyStore

import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.ssl.{ApplicationProtocolConfig, ApplicationProtocolNames, SslContext, SslContextBuilder, SslProvider, SupportedCipherSuiteFilter}
import javax.net.ssl.KeyManagerFactory

object ServerSSLHandler {

  case class ServerSSLOptions(sslContext: SslContextBuilder, httpBehaviour: SSLHttpBehaviour = SSLHttpBehaviour.Redirect)

  sealed trait SSLHttpBehaviour

  object SSLHttpBehaviour {

    case object Redirect extends SSLHttpBehaviour

    case object Accept extends SSLHttpBehaviour

    case object Fail extends SSLHttpBehaviour

  }

  def ctxSelfSigned(): SslContextBuilder = {
    import io.netty.handler.ssl.util.SelfSignedCertificate
    val ssc = new SelfSignedCertificate
    SslContextBuilder
      .forServer(ssc.certificate(), ssc.privateKey())
      .sslProvider(SslProvider.JDK)
  }

  def ctxFromKeystore(
    keyStoreInputStream: InputStream,
    keyStorePassword: String,
    certPassword: String,
  ): SslContextBuilder = {
    val keyStore: KeyStore = KeyStore.getInstance("JKS")
    keyStore.load(keyStoreInputStream, keyStorePassword.toCharArray)
    val kmf                = KeyManagerFactory.getInstance("SunX509")
    kmf.init(keyStore, certPassword.toCharArray)
    SslContextBuilder
      .forServer(kmf)
      .sslProvider(SslProvider.JDK)
  }

  def ctxFromCert(cert: InputStream, key: InputStream): SslContextBuilder = {
    SslContextBuilder
      .forServer(cert, key)
      .sslProvider(SslProvider.JDK)
  }

  def build(serverSSLOptions: ServerSSLOptions, enableHttp2: Boolean): SslContext = {
    if (serverSSLOptions == null) null
    else {
      if (enableHttp2 == true) {
        serverSSLOptions.sslContext
          .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
          .applicationProtocolConfig(
            new ApplicationProtocolConfig(
              Protocol.ALPN,
              SelectorFailureBehavior.NO_ADVERTISE,
              SelectedListenerFailureBehavior.ACCEPT,
              ApplicationProtocolNames.HTTP_2,
              ApplicationProtocolNames.HTTP_1_1,
            ),
          )
          .build()
      } else {
        serverSSLOptions.sslContext
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
  }
}