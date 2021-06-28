package zhttp.service.server
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext}
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.http2._
import zhttp.core.{JByteBuf, JChannelHandlerContext, JSharable}
import zhttp.http.Status.OK
import zhttp.http.{HttpError, HttpResult, Response, SilentResponse, _}
import zhttp.service.Server.Settings
import zhttp.service.{ChannelFuture, HttpMessageCodec, UnsafeChannelExecutor, WEB_SOCKET_HANDLER}
import zio.Exit

@JSharable
final case class Http2ServerRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  settings: Settings[R, Throwable],
) extends ChannelDuplexHandler
    with HttpMessageCodec {

  @throws[Exception]
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    super.exceptionCaught(ctx, cause)
    cause.printStackTrace()
    ctx.close
    ()
  }
  @throws[Exception]
  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    if (msg.isInstanceOf[Http2HeadersFrame]) onHeadersRead(ctx, msg.asInstanceOf[Http2HeadersFrame])
    else if (msg.isInstanceOf[Http2DataFrame]) onDataRead(ctx, msg.asInstanceOf[Http2DataFrame])
    else super.channelRead(ctx, msg)
    ()
  }
  @throws[Exception]
  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    ctx.flush
    ()
  }

  /**
   * If receive a frame with end-of-stream set, send a pre-canned response.
   */
  @throws[Exception]
  private def onDataRead(ctx: ChannelHandlerContext, data: Http2DataFrame): Unit = {
    val stream = data.stream
    if (data.isEndStream) {
      sendResponse(ctx, stream, data.content)
    } else { // We do not send back the response to the remote-peer, so we need to release it.
      data.release
    }
    // Update the flowcontroller
    ctx.write(new DefaultHttp2WindowUpdateFrame(data.initialFlowControlledBytes).stream(stream))
    ()
  }
  @throws[Exception]
  private def onHeadersRead(ctx: ChannelHandlerContext, headers: Http2HeadersFrame): Unit = {
    if (headers.isEndStream) {
      executeAsync(ctx, headers) {
        case res @ Response.HttpResponse(_, _, content) =>
          ctx.write(
            new DefaultHttp2HeadersFrame(encodeResponse(res)).stream(headers.stream()),
            ctx.channel().voidPromise(),
          )
          content match {
            case HttpData.StreamData(data)   =>
              zExec.unsafeExecute_(ctx) {
                for {
                  _ <- data.foreachChunk(c =>
                    ChannelFuture.unit(
                      ctx.writeAndFlush(
                        new DefaultHttp2DataFrame(Unpooled.copiedBuffer(c.toArray)).stream(headers.stream()),
                      ),
                    ),
                  )
                  _ <- ChannelFuture.unit(ctx.writeAndFlush(new DefaultHttp2DataFrame(true).stream(headers.stream())))
                } yield ()
              }
            case HttpData.CompleteData(data) =>
              ctx.write(
                new DefaultHttp2DataFrame(Unpooled.copiedBuffer(data.toArray), true).stream(headers.stream()),
                ctx.channel().voidPromise(),
              )
            case HttpData.Empty              =>
              ctx.write(new DefaultHttp2DataFrame(true).stream(headers.stream()), ctx.channel().voidPromise())
          }
          ()

        case res @ Response.SocketResponse(_) =>
          ctx
            .channel()
            .pipeline()
            .addLast(new WebSocketServerProtocolHandler(res.socket.config.protocol.javaConfig))
            .addLast(WEB_SOCKET_HANDLER, ServerSocketHandler(zExec, res.socket.config))
          ctx.channel().eventLoop().submit(() => ctx.fireChannelRead(headers))
          ()
      }
    }
    ()
  }

  /**
   * Sends a "Hello World" DATA frame to the client.
   */
  private def sendResponse(ctx: ChannelHandlerContext, stream: Http2FrameStream, payload: JByteBuf): Unit = { // Send a frame for the response status
    val headers = new DefaultHttp2Headers().status(OK.toJHttpStatus.codeAsText())
    ctx.write(new DefaultHttp2HeadersFrame(headers).stream(stream))
    ctx.write(new DefaultHttp2DataFrame(payload, true).stream(stream))
    ()
  }
  private def executeAsync(ctx: JChannelHandlerContext, hh: Http2HeadersFrame)(
    cb: Response[R, Throwable] => Unit,
  ): Unit =
    decodeHttp2Header(hh) match {
      case Left(err)  => cb(err.toResponse)
      case Right(req) => {
        settings.http.execute(req).evaluate match {
          case HttpResult.Empty      => cb(Response.fromHttpError(HttpError.NotFound(Path(hh.headers().path().toString))))
          case HttpResult.Success(a) => cb(a)
          case HttpResult.Failure(e) => cb(SilentResponse[Throwable].silent(e))
          case HttpResult.Effect(z)  =>
            zExec.unsafeExecute(ctx, z) {
              case Exit.Success(res)   => cb(res)
              case Exit.Failure(cause) =>
                cause.failureOption match {
                  case Some(Some(e)) => cb(SilentResponse[Throwable].silent(e))
                  case Some(None)    => cb(Response.fromHttpError(HttpError.NotFound(Path(hh.headers().path().toString))))
                  case None          => {
                    ctx.close()
                    ()
                  }
                }
            }
        }
      }
    }
}
