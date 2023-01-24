package zio.http.netty.client

import io.netty.channel.{Channel, ChannelFactory, ChannelHandler, EventLoopGroup}
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.flow.FlowControlHandler
import zio._
import zio.http._
import zio.http.netty.{ChannelFactories, EventLoopGroups, NettyRuntime, WebSocketAppHandler}
import zio.http.service._
import zio.http.socket.SocketApp
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

import scala.collection.mutable

private[zio] final case class NettyClientDriver(
  channelFactory: ChannelFactory[Channel],
  eventLoopGroup: EventLoopGroup,
  nettyRuntime: NettyRuntime,
  clientConfig: ClientConfig,
) extends ClientDriver
    with ClientRequestEncoder {
  import zio.http.ZClient.log

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
  )(implicit trace: Trace): ZIO[Scope, Throwable, ZIO[Any, Throwable, ChannelState]] = {
    encode(req).flatMap { jReq =>
      Scope.addFinalizerExit { exit =>
        ZIO.attempt {
          if (jReq.refCnt() > 0) {
            jReq.release(jReq.refCnt()): Unit
          }
        }.ignore.when(exit.isFailure)
      }.as {
        log.debug(s"Request: [${jReq.method().asciiName()} ${req.url.encode}]")

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
          pipeline.addLast(HTTP_OBJECT_AGGREGATOR, httpObjectAggregator)
          pipeline.addLast(CLIENT_INBOUND_HANDLER, clientInbound)

          toRemove.add(httpObjectAggregator)
          toRemove.add(clientInbound)
        } else {
          val flowControl   = new FlowControlHandler()
          val clientInbound =
            new ClientInboundStreamingHandler(nettyRuntime, req, onResponse, onComplete, enableKeepAlive)

          pipeline.addLast(FLOW_CONTROL_HANDLER, flowControl)
          pipeline.addLast(CLIENT_INBOUND_HANDLER, clientInbound)

          toRemove.add(flowControl)
          toRemove.add(clientInbound)
        }

        // Add WebSocketHandlers if it's a `ws` or `wss` request
        if (location.scheme.isWebSocket) {
          val headers = req.headers.encode
          val app     = createSocketApp()
          val config  = app.protocol.clientBuilder
            .customHeaders(headers)
            .webSocketUri(req.url.encode)
            .build()

          // Handles the heavy lifting required to upgrade the connection to a WebSocket connection

          val webSocketClientProtocol = new WebSocketClientProtocolHandler(config)
          val webSocket               = new WebSocketAppHandler(nettyRuntime, app, true)

          pipeline.addLast(WEB_SOCKET_CLIENT_PROTOCOL_HANDLER, webSocketClientProtocol)
          pipeline.addLast(WEB_SOCKET_HANDLER, webSocket)

          toRemove.add(webSocketClientProtocol)
          toRemove.add(webSocket)

          pipeline.fireChannelRegistered()
          pipeline.fireChannelActive()

          ZIO.succeed(
            ChannelState.Invalid,
          ) // channel becomes invalid - reuse of websocket channels not supported currently
        } else {

          pipeline.fireChannelRegistered()
          pipeline.fireChannelActive()

          val frozenToRemove = toRemove.toSet

          ZIO.attempt {
            frozenToRemove.foreach(pipeline.remove)
            ChannelState.Reusable // channel can be reused
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
    (EventLoopGroups.fromConfig ++ ChannelFactories.Client.fromConfig ++ NettyRuntime.usingDedicatedThreadPool) >>>
      ZLayer {
        for {
          eventLoopGroup <- ZIO.service[EventLoopGroup]
          channelFactory <- ZIO.service[ChannelFactory[Channel]]
          nettyRuntime   <- ZIO.service[NettyRuntime]
          clientConfig   <- ZIO.service[ClientConfig]
        } yield NettyClientDriver(channelFactory, eventLoopGroup, nettyRuntime, clientConfig)
      }

}
