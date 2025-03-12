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

import java.io.{FileInputStream, InputStream}
import java.security.KeyStore
import java.util
import javax.net.ssl.{KeyManagerFactory, TrustManagerFactory}

import scala.util.Using

import zio.Config.Secret

import zio.http.SSLConfig.{HttpBehaviour, Provider}
import zio.http.netty.Names
import zio.http.{ClientAuth, SSLConfig, Server}

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.ssl.ApplicationProtocolConfig.{
  Protocol,
  SelectedListenerFailureBehavior,
  SelectorFailureBehavior,
}
import io.netty.handler.ssl._
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{ClientAuth => NettyClientAuth}
private[netty] object SSLUtil {

  def getClientAuth(clientAuth: ClientAuth): NettyClientAuth = clientAuth match {
    case ClientAuth.Required => NettyClientAuth.REQUIRE
    case ClientAuth.Optional => NettyClientAuth.OPTIONAL
    case _                   => NettyClientAuth.NONE
  }

  implicit class SslContextBuilderOps(self: SslContextBuilder) {
    def toNettyProvider(sslProvider: Provider): SslProvider = sslProvider match {
      case Provider.OpenSSL => SslProvider.OPENSSL
      case Provider.JDK     => SslProvider.JDK
    }

    def buildWithDefaultOptions(sslConfig: SSLConfig): SslContext = {
      val clientAuthConfig: Option[ClientAuth] = sslConfig.clientAuth
      clientAuthConfig.foreach(ca => self.clientAuth(getClientAuth(ca)))
      self
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
  }

  def buildSslServerContext(
    sslConfig: SSLConfig,
    certInputStream: InputStream,
    keyInputStream: InputStream,
    trustCertCollectionPath: Option[InputStream],
  ): SslContext = {
    val sslServerContext = SslContextBuilder
      .forServer(certInputStream, keyInputStream)

    trustCertCollectionPath.foreach { stream =>
      sslServerContext.trustManager(stream)
    }

    sslServerContext.buildWithDefaultOptions(sslConfig)
  }

  private def keyManagerTrustManagerToSslContext(
    keyManagerInfo: (String, InputStream, Option[Secret]),
    trustManagerInfo: Option[(String, InputStream, Option[Secret])],
  ): SslContextBuilder = {
    val mkeyManagerFactory =
      keyManagerInfo match {
        case (keyStoreType, inputStream, maybePassword) =>
          val keyStore          = KeyStore.getInstance(keyStoreType)
          val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
          val password          = maybePassword.map(_.value.toArray).orNull

          keyStore.load(inputStream, password)
          keyManagerFactory.init(keyStore, password)
          keyManagerFactory
      }

    val mtrustManagerFactory =
      trustManagerInfo.map { case (keyStoreType, inputStream, maybePassword) =>
        val keyStore            = KeyStore.getInstance(keyStoreType)
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
        val password            = maybePassword.map(_.value.toArray).orNull

        keyStore.load(inputStream, password)
        trustManagerFactory.init(keyStore)
        trustManagerFactory
      }

    var bldr = SslContextBuilder.forServer(mkeyManagerFactory)
    mtrustManagerFactory.foreach(tmf => bldr = bldr.trustManager(tmf))
    bldr
  }

  def sslConfigToSslContext(sslConfig: SSLConfig): SslContext = sslConfig.data match {
    case SSLConfig.Data.Generate =>
      val selfSigned = new SelfSignedCertificate()
      SslContextBuilder
        .forServer(selfSigned.key, selfSigned.cert)
        .buildWithDefaultOptions(sslConfig)

    case SSLConfig.Data.FromFile(certPath, keyPath, trustCertCollectionPath) =>
      Using.Manager { use =>
        val certInputStream      = use(new FileInputStream(certPath))
        val keyInputStream       = use(new FileInputStream(keyPath))
        val trustCertInputStream = trustCertCollectionPath.map(path => use(new FileInputStream(path)))

        buildSslServerContext(
          sslConfig,
          certInputStream,
          keyInputStream,
          trustCertInputStream,
        )
      }.get

    case SSLConfig.Data.FromResource(certPath, keyPath, trustCertCollectionPath) =>
      val classLoader = getClass().getClassLoader

      Using.Manager { use =>
        val certInputStream      = use(classLoader.getResourceAsStream(certPath))
        val keyInputStream       = use(classLoader.getResourceAsStream(keyPath))
        val trustCertInputStream = trustCertCollectionPath.map(path => use(classLoader.getResourceAsStream(path)))

        buildSslServerContext(
          sslConfig,
          certInputStream,
          keyInputStream,
          trustCertInputStream,
        )
      }.get

    case SSLConfig.Data.FromJavaxNetSsl(
          keyManagerKeyStoreType,
          keyManagerSource,
          keyManagerPassword,
          trustManager,
        ) =>
      val keyManagerInfo =
        keyManagerSource match {
          case SSLConfig.Data.FromJavaxNetSsl.File(path) =>
            val inputStream = new FileInputStream(path)
            (keyManagerKeyStoreType, inputStream, keyManagerPassword)

          case SSLConfig.Data.FromJavaxNetSsl.Resource(path) =>
            val inputStream = getClass.getClassLoader.getResourceAsStream(path)
            (keyManagerKeyStoreType, inputStream, keyManagerPassword)
        }

      val trustManagerInfo =
        trustManager map { trustManager =>
          val inputStream =
            trustManager.trustManagerSource match {
              case SSLConfig.Data.FromJavaxNetSsl.File(path) =>
                new FileInputStream(path)

              case SSLConfig.Data.FromJavaxNetSsl.Resource(path) =>
                getClass.getClassLoader.getResourceAsStream(path)
            }
          (trustManager.trustManagerKeyStoreType, inputStream, trustManager.trustManagerPassword)
        }
      keyManagerTrustManagerToSslContext(keyManagerInfo, trustManagerInfo).buildWithDefaultOptions(sslConfig)
  }
}

private[zio] class ServerSSLDecoder(sslConfig: SSLConfig, cfg: Server.Config) extends ByteToMessageDecoder {

  override def decode(context: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    val pipeline      = context.channel().pipeline()
    val sslContext    = SSLUtil.sslConfigToSslContext(sslConfig)
    val httpBehaviour = sslConfig.behaviour
    if (in.readableBytes < 5)
      ()
    else if (SslHandler.isEncrypted(in, false)) {
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
