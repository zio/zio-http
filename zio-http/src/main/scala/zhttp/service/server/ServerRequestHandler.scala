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
import zio.stm.TQueue
import zio.stream.ZStream
import zio.{Exit, UIO}

/**
 * Helper class with channel methods
 */
@JSharable
final case class ServerRequestHandler[R, +E](
  zExec: UnsafeChannelExecutor[R],
  settings: Settings[R, Throwable],
) extends JSimpleChannelInboundHandler[JHttpRequest](AUTO_RELEASE_REQUEST)
    with HttpMessageCodec {

  self =>

  /**
   * Tries to release the request byte buffer, ignores if it can not.
   */

  /**
   * Asynchronously executes the Http app and passes the response to the callback.
   */
  private def executeAsync(ctx: JChannelHandlerContext, jReq: JHttpRequest)(
    cb: Response[R, Throwable, Any] => Unit,
  ): Unit =
    settings.http.execute(decodeJRequest(jReq)).evaluate match {
      case HttpResult.Empty      => cb(Response.fromHttpError(HttpError.NotFound(Path(jReq.uri()))))
      case HttpResult.Success(a) => cb(a)
      case HttpResult.Failure(e) => cb(SilentResponse[Throwable].silent(e))
      case HttpResult.Effect(z)  =>
        zExec.unsafeExecute(ctx, z) {
          case Exit.Success(res)   => cb(res)
          case Exit.Failure(cause) =>
            cause.failureOption match {
              case Some(Some(e)) => cb(SilentResponse[Throwable].silent(e))
              case Some(None)    => cb(Response.fromHttpError(HttpError.NotFound(Path(jReq.uri()))))
              case None          =>
                ctx.close()
                ()
            }
        }
    }

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
          case DecodeMap.DecodeComplete => ???
          case DecodeMap.DecodeBuffered =>
            zExec.unsafeExecute_(ctx) {
              for {
                q <- TQueue.bounded[JDefaultHttpContent](8).commit
                _ <- UIO {
                  ctx
                    .channel()
                    .pipeline()
                    //                  .addAfter(SERVER_CODEC_HANDLER, CONTINUE_100_HANDLER, new JHttpServerExpectContinueHandler())
                    .replace(this, STREAM_HANDLER, DecodeBufferedHandler[R](zExec, q))

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

      //case Decode(DecodeMap.DecodeComplete, cb) => ???
      case DecodeM(_, _)            => ???
    }
    executeAsync(ctx, jReq)(asyncWriteResponse)
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
