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

import scala.collection.mutable

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.ClientDriver.ChannelInterface
import zio.http._
import zio.http.netty._
import zio.http.netty.model.Conversions
import zio.http.netty.socket.NettySocketProtocol

import io.netty.channel.{Channel, ChannelFactory, ChannelHandler, EventLoopGroup}
import io.netty.handler.codec.http.websocketx.{WebSocketClientProtocolHandler, WebSocketFrame => JWebSocketFrame}
import io.netty.handler.codec.http.{FullHttpRequest, HttpObjectAggregator}

private[netty] final case class NettyClientDriver private (
  channelFactory: ChannelFactory[Channel],
  eventLoopGroup: EventLoopGroup,
  nettyRuntime: NettyRuntime,
  clientConfig: NettyConfig,
) extends ClientDriver {

  override type Connection = Channel

  override def requestOnChannel(
    channel: Channel,
    location: URL.Location.Absolute,
    req: Request,
    onResponse: Promise[Throwable, Response],
    onComplete: Promise[Throwable, ChannelState],
    enableKeepAlive: Boolean,
    createSocketApp: () => SocketApp[Any],
    webSocketConfig: WebSocketConfig,
  )(implicit trace: Trace): ZIO[Scope, Throwable, ChannelInterface] = {
    NettyRequestEncoder.encode(req).flatMap { jReq =>
      for {
        _     <- Scope.addFinalizer {
          ZIO.attempt {
            jReq match {
              case fullRequest: FullHttpRequest =>
                if (fullRequest.refCnt() > 0)
                  fullRequest.release(fullRequest.refCnt())
              case _                            =>
            }
          }.ignore
        }
        queue <- Queue.unbounded[WebSocketChannelEvent]
        nettyChannel     = NettyChannel.make[JWebSocketFrame](channel)
        webSocketChannel = WebSocketChannel.make(nettyChannel, queue)
        app              = createSocketApp()
        _ <- app.runZIO(webSocketChannel).ignoreLogged.interruptible.forkScoped
      } yield {
        val pipeline                              = channel.pipeline()
        val toRemove: mutable.Set[ChannelHandler] = new mutable.HashSet[ChannelHandler]()

        if (location.scheme.isWebSocket) {
          val httpObjectAggregator = new HttpObjectAggregator(Int.MaxValue)
          val inboundHandler       = new WebSocketClientInboundHandler(nettyRuntime, onResponse, onComplete)

          pipeline.addLast(Names.HttpObjectAggregator, httpObjectAggregator)
          pipeline.addLast(Names.ClientInboundHandler, inboundHandler)

          toRemove.add(httpObjectAggregator)
          toRemove.add(inboundHandler)

          val headers = Conversions.headersToNetty(req.headers)
          val config  = NettySocketProtocol
            .clientBuilder(webSocketConfig)
            .customHeaders(headers)
            .webSocketUri(req.url.encode)
            .build()

          // Handles the heavy lifting required to upgrade the connection to a WebSocket connection

          val webSocketClientProtocol = new WebSocketClientProtocolHandler(config)
          val webSocket               = new WebSocketAppHandler(nettyRuntime, queue, Some(onComplete))

          pipeline.addLast(Names.WebSocketClientProtocolHandler, webSocketClientProtocol)
          pipeline.addLast(Names.WebSocketHandler, webSocket)

          toRemove.add(webSocketClientProtocol)
          toRemove.add(webSocket)

          pipeline.fireChannelRegistered()
          pipeline.fireChannelActive()

          new ChannelInterface {
            override def resetChannel: ZIO[Any, Throwable, ChannelState] =
              ZIO.succeed(
                ChannelState.Invalid,
              ) // channel becomes invalid - reuse of websocket channels not supported currently

            override def interrupt: ZIO[Any, Throwable, Unit] =
              NettyFutureExecutor.executed(channel.disconnect())
          }
        } else {
          val clientInbound =
            new ClientInboundHandler(
              nettyRuntime,
              req,
              jReq,
              onResponse,
              onComplete,
              enableKeepAlive,
            )

          pipeline.addLast(Names.ClientInboundHandler, clientInbound)
          toRemove.add(clientInbound)

          val clientFailureHandler =
            new ClientFailureHandler(
              nettyRuntime,
              onResponse,
              onComplete,
            )
          pipeline.addLast(Names.ClientFailureHandler, clientFailureHandler)
          toRemove.add(clientFailureHandler)

          pipeline.fireChannelRegistered()
          pipeline.fireChannelActive()

          val frozenToRemove = toRemove.toSet

          new ChannelInterface {
            override def resetChannel: ZIO[Any, Throwable, ChannelState] =
              ZIO.attempt {
                frozenToRemove.foreach(pipeline.remove)
                ChannelState.Reusable // channel can be reused
              }

            override def interrupt: ZIO[Any, Throwable, Unit] =
              NettyFutureExecutor.executed(channel.disconnect())
          }
        }
      }
    }
  }

  override def createConnectionPool(dnsResolver: DnsResolver, config: ConnectionPoolConfig)(implicit
    trace: Trace,
  ): ZIO[Scope, Nothing, ConnectionPool[Channel]] =
    NettyConnectionPool
      .fromConfig(config)
      .provideSomeEnvironment[Scope](_ ++ ZEnvironment[NettyClientDriver, DnsResolver](this, dnsResolver))
}

object NettyClientDriver {
  private implicit val trace: Trace = Trace.empty

  val live: ZLayer[Any, Throwable, ClientDriver] =
    ZLayer.succeed(
      NettyConfig.default,
    ) >+> (EventLoopGroups.live ++ ChannelFactories.Client.live ++ NettyRuntime.live) >>>
      ZLayer {
        for {
          eventLoopGroup <- ZIO.service[EventLoopGroup]
          channelFactory <- ZIO.service[ChannelFactory[Channel]]
          nettyRuntime   <- ZIO.service[NettyRuntime]
          nettyConfig    <- ZIO.service[NettyConfig]
        } yield NettyClientDriver(channelFactory, eventLoopGroup, nettyRuntime, nettyConfig)
      }

}
