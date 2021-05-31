package zhttp.service.server

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.websocketx.{WebSocketServerProtocolHandler => JWebSocketServerProtocolHandler}
import io.netty.handler.codec.http.{DefaultFullHttpResponse, LastHttpContent => JLastHttpContent}
import io.netty.handler.codec.http.HttpResponseStatus.CONTINUE
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import zhttp.core.{JHttpRequest, _}
import zhttp.http.Response._
import zhttp.http._
import zhttp.service.Server.Settings
import zhttp.service._
import zio._
import zio.stm.TQueue

/**
 * Helper class with channel methods
 */
@JSharable
final case class ServerRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  settings: Settings[R, Throwable],
) extends JSimpleChannelInboundHandler[JHttpRequest](AUTO_RELEASE_REQUEST)
    with HttpMessageCodec { self =>

  /**
   * Asynchronously executes the Http app and passes the response to the callback.
   */
  private def executeAsync(ctx: JChannelHandlerContext, jReq: JHttpRequest)(
    cb: Response[R, Throwable] => Unit,
  ): Unit =
    settings.http.execute(Request(jReq)).evaluate match {
      case HttpResult.Empty      => cb(Response.fromHttpError(HttpError.NotFound(Path(jReq.uri()))))
      case HttpResult.Success(a) => cb(a)
      case HttpResult.Failure(e) => cb(responseFromThrowable(e))
      case HttpResult.Effect(z)  =>
        zExec.unsafeExecute(ctx, z) {
          case Exit.Success(res)   => cb(res)
          case Exit.Failure(cause) =>
            cause.failureOption match {
              case Some(Some(e)) => cb(responseFromThrowable(e))
              case Some(None)    => cb(Response.fromHttpError(HttpError.NotFound(Path(jReq.uri()))))
              case None          =>
                ctx.close()
                ()
            }
        }
    }

  /**
   * Creates a HttpResponse from a throwable
   */
  private def responseFromThrowable(e: Throwable) = {
    SilentResponse[Throwable].silent(e)
  }

  /**
   * Write content on the channel
   */
  private def writeContent(ctx: JChannelHandlerContext, content: HttpData[R, Throwable]): Unit = {
    content match {
      case HttpData.StreamData(data) =>
        zExec.unsafeExecute_(ctx) {
          for {
            _ <- data.foreachChunk(c => ZIO.foreach_(c)(buf => ChannelFuture.unit(ctx.writeAndFlush(buf.asJava))))
            _ <- ChannelFuture.unit(ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT))
          } yield ()
        }

      case HttpData.HBufTQueue(queue) =>
        zExec.unsafeExecute_(ctx) {
          (for {
            byte <- queue.take.commit
            isEmpty = byte.asJava.readableBytes() == 0
            _ <-
              if (isEmpty) ChannelFuture.unit(ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT))
              else ChannelFuture.unit(ctx.writeAndFlush(byte.asJava))
//            _ = println(s"Written: ${byte.asJava.readableBytes()}")
          } yield isEmpty).repeatUntil(identity)
        }

      case HttpData.CompleteData(data) =>
        ctx.write(data.asJava, ctx.channel().voidPromise())
        ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)

      case HttpData.Empty => ctx.writeAndFlush(JLastHttpContent.EMPTY_LAST_CONTENT)
      case _              => ()
    }
    ()
  }

  /**
   * Unsafe channel reader for HttpRequest
   */
  override def channelRead0(ctx: JChannelHandlerContext, jReq: JHttpRequest): Unit = {
    ctx.channel().config().setAutoRead(false)
    def asyncWriteResponse(res: Response[R, Throwable]): Unit = {
      res match {
        case FromJResponse(jRes) =>
          ctx.write(jRes, ctx.channel().voidPromise())

        case res @ HttpResponse(_, _, content) =>
          ctx.write(encodeResponse(jReq.protocolVersion(), res), ctx.channel().voidPromise())
//          releaseOrIgnore(jReq)
          writeContent(ctx, content: HttpData[R, Throwable])

        case res @ SocketResponse(_) =>
          ctx
            .channel()
            .pipeline()
            .addLast(new JWebSocketServerProtocolHandler(res.socket.config.protocol.javaConfig))
            .addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(zExec, res.socket.config))
          ctx.channel().eventLoop().submit(() => ctx.fireChannelRead(jReq))
          ()

        case Effect(program)                    =>
          zExec.unsafeExecute(ctx, program) {
            case Exit.Success(response) => asyncWriteResponse(response)
            case Exit.Failure(cause)    =>
              cause.failureOption match {
                case Some(error) => responseFromThrowable(error)
                case None        => ctx.close()
              }
          }
        case Decode(Decoder.Buffered(size), cb) =>
          zExec.unsafeExecute_(ctx) {
            for {
              q <- TQueue.bounded[HBuf1[Direction.In]](size).commit
              _ <- UIO {
                ctx
                  .channel()
                  .pipeline()
//                  .addAfter(SERVER_CODEC_HANDLER, CONTINUE_100_HANDLER, new JHttpServerExpectContinueHandler())
                  .replace(this, STREAM_HANDLER, BufferedHandler[R](zExec, q))

                ctx
                  .writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, Unpooled.EMPTY_BUFFER))
                  .addListener((_: Any) => {
                    asyncWriteResponse(cb(q))
                  })

              }
            } yield ()
          }

        case Decode(Decoder.Complete(_), _) => ???

      }
      ()
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
