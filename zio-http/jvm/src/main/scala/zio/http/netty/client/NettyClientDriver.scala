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

import scala.annotation.unroll
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.http.ClientDriver.ChannelInterface
import zio.http._
import zio.http.internal.ChannelState
import zio.http.netty._
import zio.http.netty.model.Conversions
import zio.http.netty.socket.NettySocketProtocol
import io.netty.channel.{Channel, ChannelFactory, ChannelFuture, ChannelHandlerContext, ChannelInboundHandlerAdapter, EventLoopGroup}
import io.netty.handler.codec.http.websocketx.{WebSocketClientProtocolHandler, WebSocketFrame => JWebSocketFrame}
import io.netty.handler.codec.http.{FullHttpRequest, HttpObjectAggregator}
import io.netty.handler.logging.{LoggingHandler, LogLevel => NettyLogLevel}
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
    @unroll enableInternalLogging: Boolean = false,
  )(implicit trace: Trace): ZIO[Scope, Throwable, ChannelInterface] =
      if (location.scheme.isWebSocket) {
        requestWebsocket(
          channel,
          req,
          onResponse,
          onComplete,
          createSocketApp,
          webSocketConfig,
          enableInternalLogging,
        )
      } else
        requestHttp(channel, req, onResponse, onComplete, enableKeepAlive, enableInternalLogging)

  private def requestHttp(
    channel: Channel,
    req: Request,
    onResponse: Promise[Throwable, Response],
    onComplete: Promise[Throwable, ChannelState],
    enableKeepAlive: Boolean,
    enableInternalLogging: Boolean,
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
        val pipeline = channel.pipeline()

        if (enableInternalLogging) pipeline.addLast(makeLogHandler)

        pipeline.addLast(
          Names.ClientInboundHandler,
          new ClientInboundHandler(nettyRuntime, req, jReq, onResponse, onComplete, enableKeepAlive),
        )

        pipeline
          .fireChannelRegistered()
          .fireUserEventTriggered(ClientInboundHandler.SendRequest)

        new ChannelInterface {
          override def resetChannel: ZIO[Any, Throwable, ChannelState] = {
            ZIO.attempt {
              pipeline.remove(Names.ClientInboundHandler)
              ChannelState.Reusable // channel can be reused
            }
          }

          override def interrupt: ZIO[Any, Throwable, Unit] =
            ZIO.suspendSucceed {
              val error = new InterruptedException("Netty channel operation interrupted by ZIO Http.")
              pipeline.remove(Names.ClientInboundHandler)
              onResponse.fail(error) *> NettyFutureExecutor.executed(channel.disconnect())
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
    enableInternalLogging: Boolean,
  )(implicit trace: Trace): RIO[Scope, ChannelInterface] = {
    for {
      queue              <- Queue.unbounded[WebSocketChannelEvent]
      handshakeCompleted <- Promise.make[Nothing, Boolean]
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

      if (enableInternalLogging) pipeline.addLast(makeLogHandler)

      // Handles the heavy lifting required to upgrade the connection to a WebSocket connection
      val webSocketClientProtocol = new WebSocketClientProtocolHandler(config)
      val webSocket = new WebSocketAppHandler(nettyRuntime, queue, handshakeCompleted, Some(onComplete))

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

  private def makeLogHandler = new LoggingHandler("zio.http.netty.InternalLogging", NettyLogLevel.DEBUG)
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

}
