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

import java.io.{FileInputStream, InputStream}
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory

import zio.Config.Secret
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.ClientSSLConfig

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslContext, SslContextBuilder}
object ClientSSLConverter {
  private def trustStoreToSslContext(trustStoreStream: InputStream, trustStorePassword: Secret): SslContext = {
    val trustStore          = KeyStore.getInstance("JKS")
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

    trustStore.load(trustStoreStream, trustStorePassword.value.toArray)
    trustManagerFactory.init(trustStore)
    SslContextBuilder
      .forClient()
      .trustManager(trustManagerFactory)
      .build()
  }

  private def certToSslContext(certStream: InputStream): SslContext =
    SslContextBuilder
      .forClient()
      .trustManager(certStream)
      .build()

  def toNettySSLContext(sslConfig: ClientSSLConfig): SslContext = sslConfig match {
    case ClientSSLConfig.Default =>
      SslContextBuilder
        .forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build()

    case ClientSSLConfig.FromCertFile(certPath) =>
      val certStream = new FileInputStream(certPath)
      certToSslContext(certStream)

    case ClientSSLConfig.FromCertResource(certPath) =>
      val certStream = getClass.getClassLoader.getResourceAsStream(certPath)
      certToSslContext(certStream)

    case ClientSSLConfig.FromTrustStoreFile(trustStorePath, trustStorePassword) =>
      val trustStoreStream = new FileInputStream(trustStorePath)
      trustStoreToSslContext(trustStoreStream, trustStorePassword)

    case ClientSSLConfig.FromTrustStoreResource(trustStorePath, trustStorePassword) =>
      val trustStoreStream = getClass.getClassLoader.getResourceAsStream(trustStorePath)
      trustStoreToSslContext(trustStoreStream, trustStorePassword)
  }

}
