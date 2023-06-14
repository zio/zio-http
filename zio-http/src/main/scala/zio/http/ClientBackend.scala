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

import zio.{Promise, Scope, Trace, ZIO, ZLayer}

import zio.http.ClientBackend.ChannelInterface
import zio.http.netty.client.ChannelState
import zio.http.socket.SocketApp

trait ClientBackend {
  type Connection

  def requestOnChannel(
    connection: Connection,
    location: URL.Location.Absolute,
    req: Request,
    onResponse: Promise[Throwable, Response],
    onComplete: Promise[Throwable, ChannelState],
    useAggregator: Boolean,
    enableKeepAlive: Boolean,
    createSocketApp: () => SocketApp[Any],
  )(implicit trace: Trace): ZIO[Scope, Throwable, ChannelInterface]

  def createConnectionPool(dnsResolver: DnsResolver, config: ConnectionPoolConfig)(implicit
    trace: Trace,
  ): ZIO[Scope, Nothing, ConnectionPool[Connection]]
}

object ClientBackend {
  trait ChannelInterface {
    def resetChannel: ZIO[Any, Throwable, ChannelState]
    def interrupt: ZIO[Any, Throwable, Unit]
  }

  val shared: ZLayer[ServerBackend, Throwable, ClientBackend] =
    ZLayer.scoped {
      for {
        driver        <- ZIO.service[ServerBackend]
        clientBackend <- driver.createClientBackend()
      } yield clientBackend
    }
}
