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

package zio.http.netty.client

import java.io.{File, FileInputStream, InputStream}
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

import scala.util.Using

import zio.Config.Secret
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.ClientSSLCertConfig.{FromClientCertFile, FromClientCertResource}
import zio.http.ClientSSLConfig

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContext, SslContextBuilder}
object ClientSSLConverter {
  private def trustStoreToSslContext(
    trustStoreStream: InputStream,
    trustStorePassword: Secret,
    sslContextBuilder: SslContextBuilder,
  ): SslContextBuilder = {
    val trustStore          = KeyStore.getInstance("JKS")
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

    trustStore.load(trustStoreStream, trustStorePassword.value.toArray)
    trustManagerFactory.init(trustStore)

    sslContextBuilder.trustManager(trustManagerFactory)
  }

  private def buildNettySslContextBuilder(
    sslConfig: ClientSSLConfig,
    sslContextBuilder: SslContextBuilder,
  ): SslContextBuilder = sslConfig match {
    case ClientSSLConfig.Default                                                                              =>
      sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE)
    case ClientSSLConfig.FromCertFile(certPath)                                                               =>
      val certStream = new FileInputStream(certPath)
      sslContextBuilder.trustManager(certStream)
    case ClientSSLConfig.FromCertResource(certPath)                                                           =>
      val certStream = getClass.getClassLoader.getResourceAsStream(certPath)
      sslContextBuilder.trustManager(certStream)
    case ClientSSLConfig.FromTrustStoreResource(trustStorePath, trustStorePassword)                           =>
      val trustStoreStream = getClass.getClassLoader.getResourceAsStream(trustStorePath)
      trustStoreToSslContext(trustStoreStream, trustStorePassword, sslContextBuilder)
    case ClientSSLConfig.FromClientAndServerCert(serverCertConfig, FromClientCertFile(certPath, keyPath))     =>
      val newBuilder = buildNettySslContextBuilder(serverCertConfig, sslContextBuilder)
      Using.Manager { use =>
        val certInputStream = use(new FileInputStream(new File(certPath)))
        val keyInputStream  = use(new FileInputStream(new File(keyPath)))
        newBuilder.keyManager(certInputStream, keyInputStream)
      }.get
    case ClientSSLConfig.FromClientAndServerCert(serverCertConfig, FromClientCertResource(certPath, keyPath)) =>
      val newBuilder = buildNettySslContextBuilder(serverCertConfig, sslContextBuilder)
      Using.Manager { use =>
        val classLoader     = getClass.getClassLoader
        val certInputStream = use(classLoader.getResourceAsStream(certPath))
        val keyInputStream  = use(classLoader.getResourceAsStream(keyPath))
        newBuilder.keyManager(certInputStream, keyInputStream)
      }.get
    case ClientSSLConfig.FromTrustStoreFile(trustStorePath, trustStorePassword)                               =>
      val trustStoreStream = new FileInputStream(trustStorePath)
      trustStoreToSslContext(trustStoreStream, trustStorePassword, sslContextBuilder)
  }

  def toNettySSLContext(sslConfig: ClientSSLConfig): SslContext = {
    buildNettySslContextBuilder(
      sslConfig,
      SslContextBuilder
        .forClient(),
    ).build()
  }

}
