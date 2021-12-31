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
  final def isWebSocket(res: Response[R, Throwable]): Boolean =
    res.status == Status.SWITCHING_PROTOCOLS && res.attribute.socketApp.nonEmpty

  /**
   * Checks if the response requires to switch protocol to websocket. Returns true if it can, otherwise returns false
   */
  final def upgradeToWebSocket(ctx: ChannelHandlerContext, jReq: HttpRequest, res: Response[R, Throwable]): Unit = {
    val app        = res.attribute.socketApp
    val newRequest = new DefaultFullHttpRequest(jReq.protocolVersion(), jReq.method(), jReq.uri())
    newRequest.headers().setAll(jReq.headers())
    ctx
      .channel()
      .pipeline()
      .addLast(new WebSocketServerProtocolHandler(app.config.protocol.javaConfig))
      .addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(runtime, app.config))
    ctx.channel().eventLoop().submit(() => ctx.fireChannelRead(newRequest)): Unit

  }

  val runtime: HttpRuntime[R]
}
