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

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.ClientDriver.ChannelInterface
import zio.http._
import zio.http.internal.ChannelState
import zio.http.netty._
import zio.http.netty.model.Conversions
import zio.http.netty.socket.NettySocketProtocol

import io.netty.channel.{Channel, ChannelFactory, ChannelFuture, EventLoopGroup}
import io.netty.handler.codec.PrematureChannelClosureException
import io.netty.handler.codec.http.websocketx.{WebSocketClientProtocolHandler, WebSocketFrame => JWebSocketFrame}
import io.netty.handler.codec.http.{FullHttpRequest, HttpObjectAggregator}
import io.netty.util.concurrent.GenericFutureListener

final case class NettyClientDriver private[netty] (
  channelFactory: ChannelFactory[Channel],
  eventLoopGroup: EventLoopGroup,
  nettyRuntime: NettyRuntime,
) extends ClientDriver {

  override type Connection = Channel

  override def requestOnChannel(
    channel: Channel,
    location: URL.Location.Absolute,
    req: Request,
    onResponse: Promise[Throwable, Response],
    onComplete: Promise[Throwable, ChannelState],
    enableKeepAlive: Boolean,
    createSocketApp: () => WebSocketApp[Any],
    webSocketConfig: WebSocketConfig,
  )(implicit trace: Trace): ZIO[Scope, Throwable, ChannelInterface] =
    if (location.scheme.isWebSocket)
      requestWebsocket(channel, req, onResponse, onComplete, createSocketApp, webSocketConfig)
    else
      requestHttp(channel, req, onResponse, onComplete, enableKeepAlive)

  private def requestHttp(
    channel: Channel,
    req: Request,
    onResponse: Promise[Throwable, Response],
    onComplete: Promise[Throwable, ChannelState],
    enableKeepAlive: Boolean,
  )(implicit trace: Trace): RIO[Scope, ChannelInterface] =
    ZIO
      .succeed(NettyRequestEncoder.encode(req))
      .tapSome { case fullReq: FullHttpRequest =>
        Scope.addFinalizer {
          ZIO.succeed {
            val refCount = fullReq.refCnt()
            if (refCount > 0) fullReq.release(refCount) else ()
          }
        }
      }
      .map { jReq =>
        val closeListener: GenericFutureListener[ChannelFuture] = { (_: ChannelFuture) =>
          // If onComplete was already set, it means another fiber is already in the process of fulfilling the promises
          // so we don't need to fulfill `onResponse`
          nettyRuntime.unsafeRunSync {
            onComplete.interrupt && onResponse.fail(NettyClientDriver.PrematureChannelClosure)
          }(Unsafe.unsafe, trace): Unit
        }

        val pipeline = channel.pipeline()

        pipeline.addLast(
          Names.ClientInboundHandler,
          new ClientInboundHandler(nettyRuntime, req, jReq, onResponse, onComplete, enableKeepAlive),
        )

        pipeline.addLast(
          Names.ClientFailureHandler,
          new ClientFailureHandler(onResponse, onComplete),
        )

        pipeline
          .fireChannelRegistered()
          .fireUserEventTriggered(ClientInboundHandler.SendRequest)

        channel.closeFuture().addListener(closeListener)
        new ChannelInterface {
          override def resetChannel: ZIO[Any, Throwable, ChannelState] = {
            ZIO.attempt {
              channel.closeFuture().removeListener(closeListener)
              pipeline.remove(Names.ClientInboundHandler)
              pipeline.remove(Names.ClientFailureHandler)
              ChannelState.Reusable // channel can be reused
            }
          }

          override def interrupt: ZIO[Any, Throwable, Unit] =
            ZIO.suspendSucceed {
              channel.closeFuture().removeListener(closeListener)
              NettyFutureExecutor.executed(channel.disconnect())
            }
        }
      }

  private def requestWebsocket(
    channel: Channel,
    req: Request,
    onResponse: Promise[Throwable, Response],
    onComplete: Promise[Throwable, ChannelState],
    createSocketApp: () => WebSocketApp[Any],
    webSocketConfig: WebSocketConfig,
  )(implicit trace: Trace): RIO[Scope, ChannelInterface] = {
    for {
      queue              <- Queue.unbounded[WebSocketChannelEvent]
      handshakeCompleted <- Promise.make[Nothing, Unit]
      nettyChannel     = NettyChannel.make[JWebSocketFrame](channel)
      webSocketChannel = WebSocketChannel.make(nettyChannel, queue, handshakeCompleted)
      app              = createSocketApp()
      _ <- app.handler.runZIO(webSocketChannel).ignoreLogged.interruptible.forkScoped
    } yield {
      val pipeline = channel.pipeline()

      val httpObjectAggregator = new HttpObjectAggregator(Int.MaxValue)
      val inboundHandler       = new WebSocketClientInboundHandler(onResponse, onComplete)

      pipeline.addLast(Names.HttpObjectAggregator, httpObjectAggregator)
      pipeline.addLast(Names.ClientInboundHandler, inboundHandler)

      val headers = Conversions.headersToNetty(req.headers)
      val config  = NettySocketProtocol
        .clientBuilder(app.customConfig.getOrElse(webSocketConfig))
        .customHeaders(headers)
        .webSocketUri(req.url.encode)
        .build()

      // Handles the heavy lifting required to upgrade the connection to a WebSocket connection

      val webSocketClientProtocol = new WebSocketClientProtocolHandler(config)
      val webSocket               = new WebSocketAppHandler(nettyRuntime, queue, handshakeCompleted, Some(onComplete))

      pipeline.addLast(Names.WebSocketClientProtocolHandler, webSocketClientProtocol)
      pipeline.addLast(Names.WebSocketHandler, webSocket)

      pipeline.fireChannelRegistered()
      pipeline.fireChannelActive()

      new ChannelInterface {
        override def resetChannel: ZIO[Any, Throwable, ChannelState] =
          // channel becomes invalid - reuse of websocket channels not supported currently
          Exit.succeed(ChannelState.Invalid)

        override def interrupt: ZIO[Any, Throwable, Unit] =
          NettyFutureExecutor.executed(channel.disconnect())
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

  val live: URLayer[EventLoopGroups.Config, ClientDriver] =
    (EventLoopGroups.live ++ ChannelFactories.Client.live ++ NettyRuntime.live) >>>
      ZLayer {
        for {
          eventLoopGroup <- ZIO.service[EventLoopGroup]
          channelFactory <- ZIO.service[ChannelFactory[Channel]]
          nettyRuntime   <- ZIO.service[NettyRuntime]
        } yield NettyClientDriver(channelFactory, eventLoopGroup, nettyRuntime)
      }

  private val PrematureChannelClosure = new PrematureChannelClosureException(
    "Channel closed while executing the request. This is likely caused due to a client connection misconfiguration",
  )

}
