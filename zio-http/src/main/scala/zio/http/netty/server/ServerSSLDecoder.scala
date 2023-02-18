package zio.http.netty.server

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.ssl.ApplicationProtocolConfig.{
  Protocol,
  SelectedListenerFailureBehavior,
  SelectorFailureBehavior,
}
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{SslContext, SslHandler, _}
import zio.http.SSLConfig.{HttpBehaviour, Provider}
import zio.http.netty.Names
import zio.http.{SSLConfig, ServerConfig}

import java.io.FileInputStream
import java.util
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object SSLUtil {

  implicit class SslContextBuilderOps(self: SslContextBuilder) {
    def toNettyProvider(sslProvider: Provider): SslProvider = sslProvider match {
      case Provider.OpenSSL => SslProvider.OPENSSL
      case Provider.JDK     => SslProvider.JDK
    }

    def buildWithDefaultOptions(sslConfig: SSLConfig): SslContext = self
      .sslProvider(toNettyProvider(sslConfig.provider))
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

  def sslConfigToSslContext(sslConfig: SSLConfig): SslContext = sslConfig.data match {
    case SSLConfig.Data.Generate =>
      val selfSigned = new SelfSignedCertificate()
      SslContextBuilder
        .forServer(selfSigned.key, selfSigned.cert)
        .buildWithDefaultOptions(sslConfig)

    case SSLConfig.Data.FromFile(certPath, keyPath) =>
      val certInputStream = new FileInputStream(certPath)
      val keyInputStream  = new FileInputStream(keyPath)
      SslContextBuilder
        .forServer(certInputStream, keyInputStream)
        .buildWithDefaultOptions(sslConfig)

    case SSLConfig.Data.FromResource(certPath, keyPath) =>
      val certInputStream = getClass().getClassLoader().getResourceAsStream(certPath)
      val keyInputStream  = getClass().getClassLoader().getResourceAsStream(keyPath)
      SslContextBuilder
        .forServer(certInputStream, keyInputStream)
        .buildWithDefaultOptions(sslConfig)
  }

}

private[zio] class ServerSSLDecoder(sslConfig: SSLConfig, cfg: ServerConfig) extends ByteToMessageDecoder {

  override def decode(context: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    val pipeline      = context.channel().pipeline()
    val sslContext    = SSLUtil.sslConfigToSslContext(sslConfig)
    val httpBehaviour = sslConfig.behaviour
    if (in.readableBytes < 5)
      ()
    else if (SslHandler.isEncrypted(in)) {
      pipeline.replace(this, Names.SSLHandler, sslContext.newHandler(context.alloc()))
      ()
    } else {
      httpBehaviour match {
        case HttpBehaviour.Accept =>
          pipeline.remove(this)
          ()
        case _                    =>
          pipeline.remove(Names.HttpRequestHandler)
          if (cfg.keepAlive) pipeline.remove(Names.HttpKeepAliveHandler)
          pipeline.remove(this)
          pipeline.addLast(new ServerHttpsHandler(httpBehaviour))
          ()
      }
    }
  }
}
