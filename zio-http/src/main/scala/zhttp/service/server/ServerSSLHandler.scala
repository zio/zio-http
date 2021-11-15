package zhttp.service.server

import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.ssl.ApplicationProtocolConfig.{
  Protocol,
  SelectedListenerFailureBehavior,
  SelectorFailureBehavior,
}
import io.netty.handler.ssl._

import java.io.{File, InputStream}
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

object ServerSSLHandler {

  case class ServerSSLOptions(
    sslContext: SslContextBuilder,
    httpBehaviour: SSLHttpBehaviour = SSLHttpBehaviour.Redirect,
  )

  sealed trait SSLHttpBehaviour

  object SSLHttpBehaviour {
    case object Redirect extends SSLHttpBehaviour
    case object Accept   extends SSLHttpBehaviour
    case object Fail     extends SSLHttpBehaviour
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

  def ctxFromCert(certFile: File, keyFile: File): SslContextBuilder = {
    SslContextBuilder
      .forServer(certFile, keyFile)
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
