package zhttp.service.server.content.handlers

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import io.netty.handler.codec.http2.{
  DefaultHttp2DataFrame,
  DefaultHttp2Headers,
  DefaultHttp2HeadersFrame,
  Http2FrameStream,
}
import zhttp.http.{HTTP_CHARSET, HttpData, Response, Status}
import zhttp.service.Server.Config
import zhttp.service.server.ServerTime
import zhttp.service.{ChannelFuture, HttpRuntime}

@Sharable
private[zhttp] case class Http2ServerResponseHandler[R](
  runtime: HttpRuntime[R],
  config: Config[R, Throwable],
  serverTimeGenerator: ServerTime,
) extends SimpleChannelInboundHandler[(Response, Http2FrameStream)] {
  override def channelRead0(ctx: ChannelHandlerContext, msg: (Response, Http2FrameStream)): Unit = {
    val res    = msg._1
    val stream = msg._2
    writeResponse(res, ctx, stream)
    writeData(res, ctx, stream)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }

  private def writeResponse(res: Response, ctx: ChannelHandlerContext, stream: Http2FrameStream): Unit = {

    val headers = new DefaultHttp2Headers().status(res.status.asJava.codeAsText())

    res.headers.toList.foreach(l => headers.set(l._1, l._2))

    headers
      .set(HttpHeaderNames.SERVER, "ZIO-Http")
    val length = res.data match {
      case HttpData.Empty               => 0
      case HttpData.Text(text, _)       => text.length
      case HttpData.BinaryChunk(data)   => data.length
      case HttpData.BinaryByteBuf(data) => data.toString(HTTP_CHARSET).length
      case HttpData.BinaryStream(_)     => -1
      case HttpData.RandomAccessFile(_) => -1
      case _                            => -1
      // TODO: file!
    }
    if (length >= 0) headers.setInt(HttpHeaderNames.CONTENT_LENGTH, length)
    if (res.attribute.serverTime) headers.set(HttpHeaderNames.DATE, serverTimeGenerator.refreshAndGet())
    if (res.data.isChunked) headers.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
    res.data match {
      case HttpData.RandomAccessFile(_) =>
        ctx.write(
          new DefaultHttp2HeadersFrame(
            new DefaultHttp2Headers().status(Status.UNSUPPORTED_MEDIA_TYPE.asJava.codeAsText()),
          ).stream(stream),
        )
        ctx.write(
          new DefaultHttp2DataFrame(Unpooled.copiedBuffer("Files are not supported over HTTP2", HTTP_CHARSET))
            .stream(stream),
        )
        ()
      case _                            =>
        ctx.write(
          new DefaultHttp2HeadersFrame(headers).stream(stream),
          ctx.channel().voidPromise(),
        )
        ()
    }

  }
  private def writeData(res: Response, ctx: ChannelHandlerContext, stream: Http2FrameStream): Unit = {
    res.data match {
      case HttpData.Empty               =>
        ctx.writeAndFlush(new DefaultHttp2DataFrame(true).stream(stream))
        ()
      case HttpData.Text(text, charset) =>
        ctx.writeAndFlush(
          new DefaultHttp2DataFrame(Unpooled.copiedBuffer(text, charset)).stream(stream),
        )
        ctx.write(new DefaultHttp2DataFrame(true).stream(stream))
        ()
      case HttpData.BinaryChunk(data)   =>
        ctx.writeAndFlush(
          new DefaultHttp2DataFrame(Unpooled.copiedBuffer(data.toArray)).stream(stream),
        )
        ctx.write(new DefaultHttp2DataFrame(true).stream(stream))
        ()
      case HttpData.BinaryByteBuf(data) =>
        ctx.writeAndFlush(
          new DefaultHttp2DataFrame(Unpooled.copiedBuffer(data)).stream(stream),
        )
        ctx.write(new DefaultHttp2DataFrame(true).stream(stream))
        ()
      case HttpData.BinaryStream(data)  =>
        runtime.unsafeRun(ctx) {
          for {
            _ <- data.foreach(c =>
              ChannelFuture.unit(
                ctx.writeAndFlush(
                  new DefaultHttp2DataFrame(Unpooled.copiedBuffer(c)).stream(stream),
                ),
              ),
            )
            _ <- ChannelFuture.unit(ctx.writeAndFlush(new DefaultHttp2DataFrame(true).stream(stream)))
          } yield ()
        }
      case HttpData.RandomAccessFile(_) =>
        ctx.write(new DefaultHttp2DataFrame(true).stream(stream))
        ()
      case _                            =>
        ctx.writeAndFlush(new DefaultHttp2DataFrame(true).stream(stream))
        ()
    }
  }
}
