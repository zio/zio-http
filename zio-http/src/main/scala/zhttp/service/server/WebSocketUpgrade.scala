package zhttp.service.server

import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import zhttp.http.{Response, Status}
import zhttp.service.{HttpRuntime, WEB_SOCKET_HANDLER, WebSocketAppHandler}

/**
 * Module to switch protocol to websockets
 */
trait WebSocketUpgrade[R] { self: ChannelHandler =>
  val runtime: HttpRuntime[R]

  final def isWebSocket(res: Response): Boolean =
    res.status.asJava.code() == Status.SWITCHING_PROTOCOLS.asJava.code() && res.attribute.socketApp.nonEmpty

  /**
   * Checks if the response requires to switch protocol to websocket. Returns
   * true if it can, otherwise returns false
   */
  final def upgradeToWebSocket(ctx: ChannelHandlerContext, jReq: FullHttpRequest, res: Response): Unit = {
    val app = res.attribute.socketApp

    ctx
      .channel()
      .pipeline()
      .addLast(new WebSocketServerProtocolHandler(app.get.protocol.serverBuilder.build()))
      .addLast(WEB_SOCKET_HANDLER, new WebSocketAppHandler(runtime, app.get))
    ctx.channel().eventLoop().submit(() => ctx.fireChannelRead(jReq)): Unit

  }
}
