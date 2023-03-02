package zio.http.netty.client

import scala.collection.mutable

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok

import zio.http.ClientDriver.ChannelInterface
import zio.http._
import zio.http.netty._
import zio.http.netty.model.Conversions
import zio.http.socket.SocketApp

import io.netty.channel.{Channel, ChannelFactory, ChannelHandler, EventLoopGroup}
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.flow.FlowControlHandler

final case class NettyClientDriver private (
  channelFactory: ChannelFactory[Channel],
  eventLoopGroup: EventLoopGroup,
  nettyRuntime: NettyRuntime,
  clientConfig: ClientConfig,
) extends ClientDriver
    with ClientRequestEncoder {

  override type Connection = Channel

  def requestOnChannel(
    channel: Channel,
    location: URL.Location.Absolute,
    req: Request,
    onResponse: Promise[Throwable, Response],
    onComplete: Promise[Throwable, ChannelState],
    useAggregator: Boolean,
    enableKeepAlive: Boolean,
    createSocketApp: () => SocketApp[Any],
  )(implicit trace: Trace): ZIO[Scope, Throwable, ChannelInterface] = {
    encode(req).flatMap { jReq =>
      Scope.addFinalizerExit { exit =>
        ZIO.attempt {
          if (jReq.refCnt() > 0) {
            jReq.release(jReq.refCnt()): Unit
          }
        }.ignore.when(exit.isFailure)
      }.as {
        val pipeline                              = channel.pipeline()
        val toRemove: mutable.Set[ChannelHandler] = new mutable.HashSet[ChannelHandler]()

        // ObjectAggregator is used to work with FullHttpRequests and FullHttpResponses
        // This is also required to make WebSocketHandlers work
        if (useAggregator) {
          val httpObjectAggregator = new HttpObjectAggregator(Int.MaxValue)
          val clientInbound        =
            new ClientInboundHandler(
              nettyRuntime,
              jReq,
              onResponse,
              onComplete,
              location.scheme.isWebSocket,
              enableKeepAlive,
            )
          pipeline.addLast(Names.HttpObjectAggregator, httpObjectAggregator)
          pipeline.addLast(Names.ClientInboundHandler, clientInbound)

          toRemove.add(httpObjectAggregator)
          toRemove.add(clientInbound)
        } else {
          val flowControl   = new FlowControlHandler()
          val clientInbound =
            new ClientInboundStreamingHandler(nettyRuntime, req, onResponse, onComplete, enableKeepAlive)

          pipeline.addLast(Names.FlowControlHandler, flowControl)
          pipeline.addLast(Names.ClientInboundHandler, clientInbound)

          toRemove.add(flowControl)
          toRemove.add(clientInbound)
        }

        // Add WebSocketHandlers if it's a `ws` or `wss` request
        if (location.scheme.isWebSocket) {
          val headers = Conversions.headersToNetty(req.headers)
          val app     = createSocketApp()
          val config  = app.protocol.clientBuilder
            .customHeaders(headers)
            .webSocketUri(req.url.encode)
            .build()

          // Handles the heavy lifting required to upgrade the connection to a WebSocket connection

          val webSocketClientProtocol = new WebSocketClientProtocolHandler(config)
          val webSocket               = new WebSocketAppHandler(nettyRuntime, app, true)

          pipeline.addLast(Names.WebSocketClientProtocolHandler, webSocketClientProtocol)
          pipeline.addLast(Names.WebSocketHandler, webSocket)

          toRemove.add(webSocketClientProtocol)
          toRemove.add(webSocket)

          pipeline.fireChannelRegistered()
          pipeline.fireChannelActive()

          new ChannelInterface {
            override def resetChannel(): ZIO[Any, Throwable, ChannelState] =
              ZIO.succeed(
                ChannelState.Invalid,
              ) // channel becomes invalid - reuse of websocket channels not supported currently

            override def interrupt(): ZIO[Any, Throwable, Unit] =
              NettyFutureExecutor.executed(channel.disconnect())
          }
        } else {

          pipeline.fireChannelRegistered()
          pipeline.fireChannelActive()

          val frozenToRemove = toRemove.toSet

          new ChannelInterface {
            override def resetChannel(): ZIO[Any, Throwable, ChannelState] =
              ZIO.attempt {
                frozenToRemove.foreach(pipeline.remove)
                ChannelState.Reusable // channel can be reused
              }

            override def interrupt(): ZIO[Any, Throwable, Unit] =
              NettyFutureExecutor.executed(channel.disconnect())
          }
        }
      }
    }
  }

  override def createConnectionPool(config: ConnectionPoolConfig)(implicit
    trace: Trace,
  ): ZIO[Scope, Nothing, ConnectionPool[Channel]] =
    NettyConnectionPool.fromConfig(config).provideSomeEnvironment[Scope](_ ++ ZEnvironment(this))
}

object NettyClientDriver {
  private implicit val trace: Trace = Trace.empty

  val fromConfig: ZLayer[ClientConfig, Throwable, ClientDriver] =
    (EventLoopGroups.fromConfig ++ ChannelFactories.Client.fromConfig ++ NettyRuntime.default) >>>
      ZLayer {
        for {
          eventLoopGroup <- ZIO.service[EventLoopGroup]
          channelFactory <- ZIO.service[ChannelFactory[Channel]]
          nettyRuntime   <- ZIO.service[NettyRuntime]
          clientConfig   <- ZIO.service[ClientConfig]
        } yield NettyClientDriver(channelFactory, eventLoopGroup, nettyRuntime, clientConfig)
      }

}
