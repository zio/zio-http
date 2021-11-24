package zhttp.service.server

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import zhttp.http.{Response, Status}
import zhttp.service.{UnsafeChannelExecutor, WEB_SOCKET_HANDLER}
import zhttp.socket.SocketApp

/**
 * Module to switch protocol to websockets
 */
trait WebSocketUpgrade[R] { self: ChannelHandler =>
  val zExec: UnsafeChannelExecutor[R]

  private var app: SocketApp[R, Throwable] = _

  def canSwitchProtocol(res: Response[R, Throwable]): Boolean = res.attribute.socketApp.nonEmpty

  /**
   * Checks if the response requires to switch protocol to websocket. Returns true if it can, otherwise returns false
   */
  def initializeSwitch(ctx: ChannelHandlerContext, res: Response[R, Throwable]): Unit = {
    val app = res.attribute.socketApp
    if (res.status == Status.SWITCHING_PROTOCOLS && app.nonEmpty) {
      self.app = app
      ctx.channel().config().setAutoRead(true): Unit
    }
  }

  /**
   * Returns true if the switching of protocol has been initialized
   */
  def isInitialized: Boolean = app != null

  /**
   * Switches the protocol to websocket
   */
  def switchProtocol(ctx: ChannelHandlerContext, jReq: HttpRequest): Unit = {
    ctx
      .channel()
      .pipeline()
      .addLast(new WebSocketServerProtocolHandler(app.config.protocol.javaConfig))
      .addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(zExec, app.config))
      .remove(self)
    ctx.channel().eventLoop().submit(() => ctx.fireChannelRead(toFull(jReq))): Unit
  }

  private def toFull(jReq: HttpRequest): FullHttpRequest = {
    new DefaultFullHttpRequest(
      jReq.protocolVersion(),
      jReq.method(),
      jReq.uri(),
      Unpooled.EMPTY_BUFFER,
      jReq.headers(),
      EmptyHttpHeaders.INSTANCE,
    )
  }
}
