package zhttp.service.server

import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import zhttp.http.{Response, Status}
import zhttp.service.{HttpRuntime, WEB_SOCKET_HANDLER}

/**
 * Module to switch protocol to websockets
 */
trait WebSocketUpgrade[R] { self: ChannelHandler =>
//<<<<<<< HEAD
//  final def isWebSocket(res: Response[R, Throwable]): Boolean =
//    res.status.asJava.code() == Status.SWITCHING_PROTOCOLS.asJava.code() && res.attribute.socketApp.nonEmpty
//=======
  final def isWebSocket(res: Response): Boolean =
    res.status == Status.SWITCHING_PROTOCOLS && res.attribute.socketApp.nonEmpty
//>>>>>>> dfd11acb853e6f5daf068d191193cde7dd8d146e

  /**
   * Checks if the response requires to switch protocol to websocket. Returns true if it can, otherwise returns false
   */
  final def upgradeToWebSocket(ctx: ChannelHandlerContext, jReq: FullHttpRequest, res: Response): Unit = {
    val app = res.attribute.socketApp

    ctx
      .channel()
      .pipeline()
      .addLast(new WebSocketServerProtocolHandler(app.get.protocol.javaConfig))
      .addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(runtime, app.get))
    ctx.channel().eventLoop().submit(() => ctx.fireChannelRead(jReq)): Unit

  }

  val runtime: HttpRuntime[R]
}
