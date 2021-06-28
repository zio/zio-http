package zhttp.service.server

import io.netty.buffer.{ByteBufUtil, Unpooled => JUnpooled}
import io.netty.handler.codec.http.websocketx.{WebSocketServerProtocolHandler => JWebSocketServerProtocolHandler}
import io.netty.handler.codec.http.{
  HttpResponseStatus,
  DefaultHttpContent => JDefaultHttpContent,
  LastHttpContent => JLastHttpContent,
}
import zhttp.core._
import zhttp.http.Response.{Decode, DecodeM}
import zhttp.http._
import zhttp.service.Server.Settings
import zhttp.service._
import zio.UIO
import zio.stm.TQueue
import zio.stream.ZStream

/**
 * Helper class with channel methods
 */
@JSharable
final case class ServerRequestHandler[R, +E](
  zExec: UnsafeChannelExecutor[R],
  settings: Settings[R, Throwable],
) extends JSimpleChannelInboundHandler[JHttpRequest](AUTO_RELEASE_REQUEST)
    with HttpMessageCodec
    with ExecuterHelper[R] {

  self =>

  /**
   * Tries to release the request byte buffer, ignores if it can not.
   */

  private def writeContent(ctx: JChannelHandlerContext, content: Content[R, Throwable, Any]) = content match {
    case Content.BufferedContent(data) =>
      zExec.unsafeExecute_(ctx) {
        for {
          _ <- data.foreachChunk(c =>
            ChannelFuture.unit(
              ctx.writeAndFlush(JUnpooled.wrappedBuffer(c.toArray)),
            ),
          )
          _ <- ChannelFuture.unit(ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT))
        } yield ()
      }
    case Content.CompleteContent(data) =>
      ctx.write(JUnpooled.copiedBuffer(data.toArray), ctx.channel().voidPromise())
      ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)
    case Content.EmptyContent          => ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)
  }

  /**
   * Unsafe channel reader for HttpRequest
   */
  override def channelRead0(ctx: JChannelHandlerContext, jReq: JHttpRequest): Unit = {
    ctx.channel().config().setAutoRead(false)
    def asyncWriteResponse(res: Response[R, Throwable, Any]): Unit = res match {
      case res @ Response.Default(_, _, content) =>
        ctx.write(
          encodeResponse(jReq.protocolVersion(), res),
          ctx.channel().voidPromise(),
        )
        writeContent(ctx, content)
        ()

      case res @ Response.Socket(_) =>
        ctx
          .channel()
          .pipeline()
          .addLast(new JWebSocketServerProtocolHandler(res.socket.config.protocol.javaConfig))
          .addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(zExec, res.socket.config))
        ctx.channel().eventLoop().submit(() => ctx.fireChannelRead(jReq))
        ()
      case Decode(d, cb)            =>
        d match {
          case DecodeMap.DecodeComplete => {
            println("DecodeComplete")
            val p = ctx.channel().pipeline()
            p.addAfter(SERVER_CODEC_HANDLER, OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
            if (p.get(STREAM_HANDLER) != null) p.remove(STREAM_HANDLER) else ()
            p.replace(this, COMPLETE_HANDLER, DecodeCompleteHandler[R](zExec, settings, jReq, cb))
            ()
          }
          case DecodeMap.DecodeBuffered => {
            println("DecodeBuffered")
            zExec.unsafeExecute_(ctx) {
              for {
                q <- TQueue.bounded[JDefaultHttpContent](8).commit
                _ <- UIO {
                  val p = ctx.channel().pipeline()
                  if (p.get(COMPLETE_HANDLER) != null) p.remove(COMPLETE_HANDLER) else ()
                  if (p.get(COMPLETE_HANDLER) != null) {
                    p.remove(COMPLETE_HANDLER)
                    p.remove(OBJECT_AGGREGATOR)
                  } else ()
                  p.replace(this, STREAM_HANDLER, DecodeBufferedHandler[R](zExec, q))
                  ctx
                    .writeAndFlush(
                      new JDefaultFullHttpResponse(jReq.protocolVersion(), HttpResponseStatus.CONTINUE, false),
                    )
                    .addListener((_: Any) => {
                      asyncWriteResponse(
                        cb(
                          ZStream
                            .fromTQueue(q)
                            .takeUntil(b => b.isInstanceOf[JLastHttpContent])
                            .flatMap(b => ZStream.fromIterable(ByteBufUtil.getBytes(b.content()))),
                        ),
                      )
                    })
                }
              } yield ()
            }
          }
        }

      //case Decode(DecodeMap.DecodeComplete, cb) => ???
      case DecodeM(d, _)            =>
        d match {
          case DecodeMap.DecodeComplete => ???
          case DecodeMap.DecodeBuffered => ???
        }
    }
    executeAsync(zExec, settings, ctx, decodeJRequest(jReq))(asyncWriteResponse)
  }

  /**
   * Handles exceptions that throws
   */
  override def exceptionCaught(ctx: JChannelHandlerContext, cause: Throwable): Unit = {
    settings.error match {
      case Some(v) => zExec.unsafeExecute_(ctx)(v(cause).uninterruptible)
      case None    =>
        ctx.fireExceptionCaught(cause)
        ()
    }
  }

}
