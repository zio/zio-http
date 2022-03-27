package zhttp.service.server

import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import zhttp.http.{Response, Status}
import zhttp.service.{HttpRuntime, WEB_SOCKET_HANDLER, WebSocketAppHandler}

import scala.annotation.tailrec

/**
 * Module to switch protocol to websockets
 */
trait WebSocketUpgrade[R] { self: ChannelHandler =>
  val runtime: HttpRuntime[R]

  final def isWebSocket(res: Response): Boolean =
    res.status.asJava.code() == Status.SwitchingProtocols.asJava.code() && res.attribute.socketApp.nonEmpty

  /**
   * Checks if the response requires to switch protocol to websocket. Returns
   * true if it can, otherwise returns false
   */
  @tailrec
  final def upgradeToWebSocket(jReq: HttpRequest, res: Response)(implicit ctx: ChannelHandlerContext): Unit = {
    val app = res.attribute.socketApp

    jReq match {
      case jReq: FullHttpRequest =>
        ctx
          .channel()
          .pipeline()
          .addLast(new WebSocketServerProtocolHandler(app.get.protocol.serverBuilder.build()))
          .addLast(WEB_SOCKET_HANDLER, new WebSocketAppHandler("Server", runtime, app.get))
        ctx.channel().eventLoop().submit(() => ctx.fireChannelRead(jReq)): Unit

      case jReq: HttpRequest =>
        val fullRequest = new DefaultFullHttpRequest(jReq.protocolVersion(), jReq.method(), jReq.uri())
        fullRequest.headers().setAll(jReq.headers())
        self.upgradeToWebSocket(fullRequest, res)
    }
  }
}
