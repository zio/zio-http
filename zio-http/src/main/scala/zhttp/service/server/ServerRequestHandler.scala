package zhttp.service.server

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.websocketx.{WebSocketServerHandshakerFactory => JWebSocketServerHandshakerFactory}
import io.netty.handler.codec.http.{
  DefaultHttpRequest,
  HttpHeaderNames => JHttpHeaderNames,
  HttpResponseStatus => JHttpResponseStatus,
  HttpVersion => JHttpVersion,
}
import io.netty.util.CharsetUtil
import zhttp.core.{JHttpObjectAggregator, _}
import zhttp.http.{Response, _}
import zhttp.service._
import zio.Exit

import scala.annotation.unused

/**
 * Helper class with channel methods
 */
final case class ServerRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  app: HttpApp[R, Nothing],
) extends JChannelInboundHandlerAdapter
    with ServerJHttpRequestDecoder
    with ServerHttpExceptionHandler { self =>

  /**
   * Executes the current app asynchronously
   */
  private def execute(ctx: JChannelHandlerContext, req: => Request)(success: Response => Unit): Unit = {
    app.eval(req) match {
      case HttpResult.Success(a)    =>
        success(a)
      case HttpResult.Continue(zio) =>
        zExec.unsafeExecute(ctx, zio) {
          case Exit.Success(res) => success(res)
          case _                 => ()
        }
      case _                        => ()
    }
  }

  /**
   * Tries to release the request byte buffer, ignores if it can not.
   */
  def releaseOrIgnore(jReq: JFullHttpRequest): Boolean = jReq.release(jReq.content().refCnt())

  def webSocketUpgrade(
    ctx: JChannelHandlerContext,
    jReq: JHttpRequest,
    res: Response.SocketResponse,
  ): Unit = {
    val hh = new JWebSocketServerHandshakerFactory(jReq.uri(), res.subProtocol.orNull, false).newHandshaker(jReq)
    if (hh == null) {
      JWebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel, ctx.channel().voidPromise())
    } else {
      try {
        // handshake can throw
        hh.handshake(ctx.channel(), jReq).addListener { (future: JChannelFuture) =>
          if (!future.isSuccess) {
            ctx.fireExceptionCaught(future.cause)
            ()
          } else {
            val pl = ctx.channel().pipeline()
            pl.addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(zExec, res.socket))
            pl.remove(HTTP_REQUEST_HANDLER)
            ()
          }
        }
      } catch {
        case _: JWebSocketHandshakeException =>
          JWebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel, ctx.channel().voidPromise())
      }
    }
    ()
  }

  def writeAndFlush(ctx: JChannelHandlerContext, @unused jReq: JHttpRequest, @unused res: Response): Unit = {
    val buf = Unpooled.copiedBuffer("Hello Response", CharsetUtil.UTF_8)

    val jRes = new JDefaultFullHttpResponse(
      JHttpVersion.HTTP_1_1,
      JHttpResponseStatus.OK,
      buf,
    )

    jRes.headers.set(JHttpHeaderNames.CONTENT_LENGTH, buf.readableBytes)
    ctx.writeAndFlush(jRes)
    ()
    /*res match {
      case Response.HttpResponse(_, _, _)      =>
        ctx.writeAndFlush(jRes, ctx.voidPromise())
        ()
      case res @ Response.SocketResponse(_, _) =>
        self.webSocketUpgrade(ctx, jReq, res)
        ()
    }*/
  }

  /**
   * Unsafe channel reader for HttpRequest
   */
  override def channelRead(ctx: JChannelHandlerContext, msg: Any): Unit = {
    val flag = System.getenv("HARDCODE")
    if (flag == null) {
      msg match {
        case jHttpRequest: DefaultHttpRequest =>
          if (jHttpRequest.headers().contains(JHttpHeaderNames.CONTENT_LENGTH)) addAggregator(ctx)
          else execute(ctx, unsafelyDecodeJHttpRequest(jHttpRequest))(writeAndFlush(ctx, jHttpRequest, _))

        case jFullHttpRequest: JFullHttpRequest =>
          execute(ctx, unsafelyDecodeJFullHttpRequest(jFullHttpRequest)) { res =>
            writeAndFlush(ctx, jFullHttpRequest, res)
            releaseOrIgnore(jFullHttpRequest)
            ()
          }

        case _ => ()
      }
    } else {
      if (msg.isInstanceOf[DefaultHttpRequest])
        writeAndFlush(ctx, null, Response.text("Hello Slow"))
    }

  }

  /**
   * Adds object aggregator and resets the current channel handler
   */
  private def addAggregator(ctx: JChannelHandlerContext): Unit = {
    ctx.channel().pipeline.addBefore(HTTP_REQUEST_HANDLER, OBJECT_AGGREGATOR, new JHttpObjectAggregator(Int.MaxValue))
    ()
  }

  /**
   * Handles exceptions that throws
   */
  override def exceptionCaught(ctx: JChannelHandlerContext, cause: Throwable): Unit = {
    if (self.canThrowException(cause)) {
      super.exceptionCaught(ctx, cause)
    }
  }
}
