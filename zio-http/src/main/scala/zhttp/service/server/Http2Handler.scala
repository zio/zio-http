package zhttp.service.server
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled.{copiedBuffer, unreleasableBuffer}
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext}
import io.netty.handler.codec.http2._
import io.netty.util.CharsetUtil
import zhttp.core.{JByteBuf, JChannelHandlerContext}
import zhttp.http.{HttpError, HttpResult, Response, SilentResponse}
import zhttp.http.Status.OK
import zhttp.service.Server.Settings
import zhttp.service.{HttpMessageCodec, UnsafeChannelExecutor}
import zio.Exit
import zhttp.http._
class Http2Handler[R](
                       zExec: UnsafeChannelExecutor[R],
                       settings: Settings[R, Throwable],
                     ) extends ChannelDuplexHandler with HttpMessageCodec{
  val RESPONSE_BYTES: JByteBuf = unreleasableBuffer(copiedBuffer("Hello Woorld", CharsetUtil.UTF_8))
  @throws[Exception]
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    println("exceptionCaught")
    super.exceptionCaught(ctx, cause)
    cause.printStackTrace()
    ctx.close
    ()
  }
  @throws[Exception]
  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    println("channelRead")
    println(msg)
    if (msg.isInstanceOf[Http2HeadersFrame]) onHeadersRead(ctx, msg.asInstanceOf[Http2HeadersFrame])
    else if (msg.isInstanceOf[Http2DataFrame]) onDataRead(ctx, msg.asInstanceOf[Http2DataFrame])
    else super.channelRead(ctx, msg)
    ()
  }
  @throws[Exception]
  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    println("channelReadComplete")
    ctx.flush
    ()
  }
  /**
   * If receive a frame with end-of-stream set, send a pre-canned response.
   */
  @throws[Exception]
  private def onDataRead(ctx: ChannelHandlerContext, data: Http2DataFrame): Unit = {
    println("onDataRead")
    println(data)
    val stream = data.stream
    if (data.isEndStream) {
      println(data)
      println(data.content())
      println(data.stream())
      sendResponse(ctx, stream, data.content)
    }
    else { // We do not send back the response to the remote-peer, so we need to release it.
      println(data)
      println(data.content())
      println(data.stream())
      data.release
    }
    // Update the flowcontroller
    ctx.write(new DefaultHttp2WindowUpdateFrame(data.initialFlowControlledBytes).stream(stream))
    ()
  }
  @throws[Exception]
  private def onHeadersRead(ctx: ChannelHandlerContext, headers: Http2HeadersFrame): Unit = {
    println("onHeaerRead")
    println(headers)
    if (headers.isEndStream) {
      val content = ctx.alloc.buffer
      content.writeBytes(RESPONSE_BYTES.duplicate)
      ByteBufUtil.writeAscii(content, " - via HTTP/2")
      sendResponse(ctx, headers.stream, content)
    }
    ()
  }
  /**
   * Sends a "Hello World" DATA frame to the client.
   */
  private def sendResponse(ctx: ChannelHandlerContext, stream: Http2FrameStream, payload: JByteBuf): Unit = { // Send a frame for the response status
    println("sendResponse")
    println(payload)
    val headers = new DefaultHttp2Headers().status(OK.toJHttpStatus.codeAsText())
    ctx.write(new DefaultHttp2HeadersFrame(headers).stream(stream))
    ctx.write(new DefaultHttp2DataFrame(payload, true).stream(stream))
    ()
  }
  private def executeAsync(ctx: JChannelHandlerContext, hh: DefaultHttp2HeadersFrame)(
    cb: Response[R, Throwable] => Unit,
  ): Unit =
    decodeHttp2Header(hh) match {
      case Left(err)  => cb(err.toResponse)
      case Right(req) =>
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