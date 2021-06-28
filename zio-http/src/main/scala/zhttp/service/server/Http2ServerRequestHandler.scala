package zhttp.service.server
import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.channel.{ChannelDuplexHandler => JChannelDuplexHandler}
import io.netty.handler.codec.http.websocketx.{WebSocketServerProtocolHandler => JWebSocketServerProtocolHandler}
import io.netty.handler.codec.http2.{
  DefaultHttp2DataFrame => JDefaultHttp2DataFrame,
  DefaultHttp2Headers => JDefaultHttp2Headers,
  DefaultHttp2HeadersFrame => JDefaultHttp2HeadersFrame,
  DefaultHttp2WindowUpdateFrame => JDefaultHttp2WindowUpdateFrame,
  Http2DataFrame => JHttp2DataFrame,
  Http2FrameStream => JHttp2FrameStream,
  Http2HeadersFrame => JHttp2HeadersFrame,
}
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
) extends JChannelDuplexHandler
    with HttpMessageCodec {

  @throws[Exception]
  override def exceptionCaught(ctx: JChannelHandlerContext, cause: Throwable): Unit = {
    super.exceptionCaught(ctx, cause)
    cause.printStackTrace()
    ctx.close
    ()
  }
  @throws[Exception]
  override def channelRead(ctx: JChannelHandlerContext, msg: Any): Unit = {
    if (msg.isInstanceOf[JHttp2HeadersFrame]) onHeadersRead(ctx, msg.asInstanceOf[JHttp2HeadersFrame])
    else if (msg.isInstanceOf[JHttp2DataFrame]) onDataRead(ctx, msg.asInstanceOf[JHttp2DataFrame])
    else super.channelRead(ctx, msg)
    ()
  }
  @throws[Exception]
  override def channelReadComplete(ctx: JChannelHandlerContext): Unit = {
    ctx.flush
    ()
  }

  /**
   * If receive a frame with end-of-stream set, send a pre-canned response.
   */
  @throws[Exception]
  private def onDataRead(ctx: JChannelHandlerContext, data: JHttp2DataFrame): Unit = {
    val stream = data.stream
    if (data.isEndStream) {
      sendResponse(ctx, stream, data.content)
    } else { // We do not send back the response to the remote-peer, so we need to release it.
      data.release
    }
    // Update the flowcontroller
    ctx.write(new JDefaultHttp2WindowUpdateFrame(data.initialFlowControlledBytes).stream(stream))
    ()
  }
  @throws[Exception]
  private def onHeadersRead(ctx: JChannelHandlerContext, headers: JHttp2HeadersFrame): Unit = {
    if (headers.isEndStream) {
      executeAsync(ctx, headers) {
        case res @ Response.HttpResponse(_, _, content) =>
          ctx.write(
            new JDefaultHttp2HeadersFrame(encodeResponse(res)).stream(headers.stream()),
            ctx.channel().voidPromise(),
          )
          content match {
            case HttpData.StreamData(data)   =>
              zExec.unsafeExecute_(ctx) {
                for {
                  _ <- data.foreachChunk(c =>
                    ChannelFuture.unit(
                      ctx.writeAndFlush(
                        new JDefaultHttp2DataFrame(JUnpooled.copiedBuffer(c.toArray)).stream(headers.stream()),
                      ),
                    ),
                  )
                  _ <- ChannelFuture.unit(ctx.writeAndFlush(new JDefaultHttp2DataFrame(true).stream(headers.stream())))
                } yield ()
              }
            case HttpData.CompleteData(data) =>
              ctx.write(
                new JDefaultHttp2DataFrame(JUnpooled.copiedBuffer(data.toArray), true).stream(headers.stream()),
                ctx.channel().voidPromise(),
              )
            case HttpData.Empty              =>
              ctx.write(new JDefaultHttp2DataFrame(true).stream(headers.stream()), ctx.channel().voidPromise())
          }
          ()

        case res @ Response.SocketResponse(_) =>
          ctx
            .channel()
            .pipeline()
            .addLast(new JWebSocketServerProtocolHandler(res.socket.config.protocol.javaConfig))
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
  private def sendResponse(ctx: JChannelHandlerContext, stream: JHttp2FrameStream, payload: JByteBuf): Unit = { // Send a frame for the response status
    val headers = new JDefaultHttp2Headers().status(OK.toJHttpStatus.codeAsText())
    ctx.write(new JDefaultHttp2HeadersFrame(headers).stream(stream))
    ctx.write(new JDefaultHttp2DataFrame(payload, true).stream(stream))
    ()
  }
  private def executeAsync(ctx: JChannelHandlerContext, hh: JHttp2HeadersFrame)(
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
