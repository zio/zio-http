package zhttp.service.server

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import zhttp.http.Response
import zhttp.service.server.WebSocketUpgrade.log
import zhttp.service.{Handler, Log, WEB_SOCKET_HANDLER, WebSocketAppHandler}

import scala.annotation.tailrec

/**
 * Module to switch protocol to websockets
 */
trait WebSocketUpgrade[R] { self: Handler[R] =>

  /**
   * Checks if the response requires to switch protocol to websocket. Returns
   * true if it can, otherwise returns false
   */
  @tailrec
  final def upgradeToWebSocket(jReq: HttpRequest, res: Response)(implicit ctx: ChannelHandlerContext): Unit = {
    val app = res.attribute.socketApp
    jReq match {
      case jReq: FullHttpRequest =>
        log.debug(s"Upgrading to WebSocket: [${jReq.uri()}]")
        log.debug(s"SocketApp: [${app.orNull}]")
        ctx
          .channel()
          .pipeline()
          .addLast(new WebSocketServerProtocolHandler(app.get.protocol.serverBuilder.build()))
          .addLast(WEB_SOCKET_HANDLER, new WebSocketAppHandler(runtime, app.get, false))

        val retained = jReq.retainedDuplicate()
        ctx.channel().eventLoop().submit { () => ctx.fireChannelRead(retained) }: Unit

      case jReq: HttpRequest =>
        val fullRequest = new DefaultFullHttpRequest(jReq.protocolVersion(), jReq.method(), jReq.uri())
        fullRequest.headers().setAll(jReq.headers())
        self.upgradeToWebSocket(fullRequest, res)
    }
  }
}

object WebSocketUpgrade {
  private[zhttp] val log = Log.withTags("Server", "WebSocket")
}
