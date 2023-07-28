/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.netty.server

import java.io.FileInputStream
import java.util

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.SSLConfig.{HttpBehaviour, Provider}
import zio.http.netty.Names
import zio.http.{SSLConfig, Server}

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

private[zio] class ServerSSLDecoder(sslConfig: SSLConfig, cfg: Server.Config) extends ByteToMessageDecoder {

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
