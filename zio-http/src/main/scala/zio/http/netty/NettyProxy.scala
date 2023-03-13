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

package zio.http.netty

import java.net.InetSocketAddress

import zio.http.Proxy
import zio.http.middleware.Auth.Credentials
import zio.http.netty.model.Conversions

import io.netty.handler.proxy.HttpProxyHandler

class NettyProxy private (proxy: Proxy) {

  /**
   * Converts a Proxy to [io.netty.handler.proxy.HttpProxyHandler]
   */
  def encode: Option[HttpProxyHandler] = proxy.credentials.fold(unauthorizedProxy)(authorizedProxy)

  private def authorizedProxy(credentials: Credentials): Option[HttpProxyHandler] = for {
    proxyAddress <- buildProxyAddress
    uname          = credentials.uname
    upassword      = credentials.upassword
    encodedHeaders = Conversions.headersToNetty(proxy.headers)
  } yield new HttpProxyHandler(proxyAddress, uname, upassword, encodedHeaders)

  private def unauthorizedProxy: Option[HttpProxyHandler] = for {
    proxyAddress <- buildProxyAddress
    encodedHeaders = Conversions.headersToNetty(proxy.headers)
  } yield {
    new HttpProxyHandler(proxyAddress, encodedHeaders)
  }

  private def buildProxyAddress: Option[InetSocketAddress] = for {
    proxyHost <- proxy.url.host
    proxyPort <- proxy.url.port
  } yield new InetSocketAddress(proxyHost, proxyPort)
}

object NettyProxy {
  def fromProxy(proxy: Proxy): NettyProxy = new NettyProxy(proxy)
}
