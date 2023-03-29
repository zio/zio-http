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

package zio.http

import java.net.InetSocketAddress

import zio.{Config, Duration, ZLayer}

import zio.http.netty.{ChannelType, EventLoopGroups}
import zio.http.socket.SocketApp

final case class ClientConfig(
  socketApp: Option[SocketApp[Any]] = None,
  ssl: Option[ClientSSLConfig] = None,
  proxy: Option[Proxy] = None,
  channelType: ChannelType = ChannelType.AUTO,
  nThreads: Int = 0,
  useAggregator: Boolean = true,
  connectionPool: ConnectionPoolConfig = ConnectionPoolConfig.Disabled,
  maxHeaderSize: Int = 8192,
  requestDecompression: Decompression = Decompression.No,
  localAddress: Option[InetSocketAddress] = None,
) extends EventLoopGroups.Config {
  self =>
  def ssl(ssl: ClientSSLConfig): ClientConfig = self.copy(ssl = Some(ssl))

  def socketApp(socketApp: SocketApp[Any]): ClientConfig = self.copy(socketApp = Some(socketApp))

  def proxy(proxy: Proxy): ClientConfig = self.copy(proxy = Some(proxy))

  def channelType(channelType: ChannelType): ClientConfig = self.copy(channelType = channelType)

  def maxThreads(nThreads: Int): ClientConfig = self.copy(nThreads = nThreads)

  def useObjectAggregator(objectAggregator: Boolean): ClientConfig = self.copy(useAggregator = objectAggregator)

  def withFixedConnectionPool(size: Int): ClientConfig =
    self.copy(connectionPool = ConnectionPoolConfig.Fixed(size))

  def withDynamicConnectionPool(minimum: Int, maximum: Int, ttl: Duration): ClientConfig =
    self.copy(connectionPool = ConnectionPoolConfig.Dynamic(minimum = minimum, maximum = maximum, ttl = ttl))

  /**
   * Configure the client to use `maxHeaderSize` value when encode/decode
   * headers.
   */
  def maxHeaderSize(headerSize: Int): ClientConfig = self.copy(maxHeaderSize = headerSize)

  def requestDecompression(isStrict: Boolean): ClientConfig =
    self.copy(requestDecompression = if (isStrict) Decompression.Strict else Decompression.NonStrict)
}

object ClientConfig {
  val default: ZLayer[Any, Nothing, ClientConfig] =
    live(empty)

  val empty: ClientConfig = ClientConfig()

  def live(clientConfig: ClientConfig): ZLayer[Any, Nothing, ClientConfig] =
    ZLayer.succeed(clientConfig)
}
