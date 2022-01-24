package zhttp.service.server

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.unix.Errors.NativeIoException
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext, DefaultFileRegion}
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http2._
import zhttp.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND, UPGRADE_REQUIRED}
import zhttp.http._
import zhttp.service.Server.Config
import zhttp.service._
import zio.{Chunk, UIO, ZIO}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable.Map
//TODO: update the handler for files and httpdata
@Sharable
final case class Http2ServerRequestHandler[R] private[zhttp] (
  runtime: HttpRuntime[R],
  settings: Config[R, Throwable],
) extends ChannelDuplexHandler
    with HttpMessageCodec
    with WebSocketUpgrade[R] { self =>
  val hedaerMap: Map[Int, Http2HeadersFrame]         = Map.empty[Int, Http2HeadersFrame]
  val dataMap: Map[Int, List[DefaultHttp2DataFrame]] = Map.empty[Int, List[DefaultHttp2DataFrame]]

  @throws[Exception]
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    settings.error match {
      case Some(v) => runtime.unsafeRun(ctx)(v(cause).uninterruptible)
      case None    =>
        if (ignoreException(cause)) {
          if (ctx.channel.isActive) {
            ctx.close
            ()
          }
        } else ctx.fireExceptionCaught(cause)
        ()
    }
  }

  private def ignoreException(throwable: Throwable): Boolean =
    throwable.isInstanceOf[NativeIoException]

  /**
   * Executes program
   */
  def unsafeRunZIO(program: ZIO[R, Throwable, Any], ctx: ChannelHandlerContext): Unit = runtime.unsafeRun(ctx) {
    program
  }

  @throws[Exception]
  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    if (msg.isInstanceOf[Http2HeadersFrame]) {
      onHeaderRead(ctx, msg.asInstanceOf[Http2HeadersFrame])
    } else if (msg.isInstanceOf[DefaultHttp2DataFrame]) {
      onDataRead(ctx, msg.asInstanceOf[DefaultHttp2DataFrame])
    } else super.channelRead(ctx, msg)
    ()
  }

  private def onHeaderRead(ctx: ChannelHandlerContext, header: Http2HeadersFrame): Unit = {
    if (header.isEndStream) {
      onEndStream(ctx, header)
    } else {
      hedaerMap.put(header.stream().id(), header)
      ()
    }
  }

  private def onDataRead(ctx: ChannelHandlerContext, data: DefaultHttp2DataFrame) = {
    val stream = data.stream().id()
    if (data.isEndStream) {
      if (hedaerMap.contains(stream)) {
        val header = hedaerMap.get(stream).get
        if (dataMap.contains(stream)) {
          onEndStream(ctx, header, dataMap.get(stream).get :+ data)
        } else {
          onEndStream(ctx, header, List(data))
        }
      } else {
        if (dataMap.contains(stream)) {
          dataMap.update(stream, dataMap.get(stream).get :+ data)
        } else {
          dataMap.put(stream, List(data))
        }
      }
    } else {
      if (dataMap.contains(stream)) {
        dataMap.update(stream, dataMap.get(stream).get :+ data)
      } else {
        dataMap.put(stream, List(data))
      }
    }
  }

  @throws[Exception]
  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    ctx.flush
    ()
  }

  @throws[Exception]
  private def onEndStream(
    ctx: ChannelHandlerContext,
    headers: Http2HeadersFrame,
    dataL: List[DefaultHttp2DataFrame] = null,
  ): Unit = {
    decodeHttp2Header(headers, ctx, dataL) match {
      case Left(err)   => unsafeWriteAndFlushErrorResponse(err.getCause, ctx, headers.stream())
      case Right(jReq) => {
        settings.app.execute(jReq) match {
          case HExit.Failure(e)   => unsafeWriteAndFlushErrorResponse(e, ctx, headers.stream())
          case HExit.Empty        => unsafeWriteAndFlushEmptyResponse(ctx, headers.stream())
          case HExit.Success(res) =>
            if (self.isWebSocket(res)) {
              unsafeWriteAnyResponse(
                Response(
                  UPGRADE_REQUIRED,
                  data = HttpData.fromString("Websockets are not supported over HTTP/2. Make HTTP/1.1 connection."),
                ),
                ctx,
                headers.stream(),
              )
            } else {
              unsafeWriteAnyResponse(res, ctx, headers.stream())
            }
          case HExit.Effect(resM) =>
            unsafeRunZIO(
              resM.foldM(
                {
                  case Some(cause) => UIO(unsafeWriteAndFlushErrorResponse(cause, ctx, headers.stream()))
                  case None        => UIO(unsafeWriteAndFlushEmptyResponse(ctx, headers.stream()))
                },
                res =>
                  if (self.isWebSocket(res))
                    UIO(
                      unsafeWriteAnyResponse(
                        Response(
                          UPGRADE_REQUIRED,
                          data =
                            HttpData.fromString("Websockets are not supported over HTTP/2. Make HTTP/1.1 connection."),
                        ),
                        ctx,
                        headers.stream(),
                      ),
                    )
                  else {
                    for {
                      _ <- UIO(unsafeWriteAnyResponse(res, ctx, headers.stream()))
                    } yield ()
                  },
              ),
              ctx,
            )
        }
      }
    }
  }

  /**
   * Writes error response to the Channel
   */
  private def unsafeWriteAndFlushErrorResponse(
    cause: Throwable,
    ctx: ChannelHandlerContext,
    stream: Http2FrameStream,
  ): Unit = {
    val headers = new DefaultHttp2Headers().status(INTERNAL_SERVER_ERROR.asJava.codeAsText())
    headers
      .set(HttpHeaderNames.SERVER, "ZIO-Http")
      .set(HttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
    val content = Chunk.fromArray(cause.toString.getBytes(HTTP_CHARSET))
    headers.setInt(HttpHeaderNames.CONTENT_LENGTH, content.length)
    ctx.write(
      new DefaultHttp2HeadersFrame(headers).stream(stream),
      ctx.channel().voidPromise(),
    )
    ctx.writeAndFlush(
      new DefaultHttp2DataFrame(JUnpooled.copiedBuffer(content.toArray)).stream(stream),
    )
    ctx.write(new DefaultHttp2DataFrame(true).stream(stream))
    ()
  }

  /**
   * Writes not found error response to the Channel
   */
  def unsafeWriteAndFlushEmptyResponse(ctx: ChannelHandlerContext, stream: Http2FrameStream): Unit = {
    val headers = new DefaultHttp2Headers().status(NOT_FOUND.asJava.codeAsText())
    headers
      .set(HttpHeaderNames.SERVER, "ZIO-Http")
      .set(HttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
      .setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
    ctx.write(new DefaultHttp2HeadersFrame(headers).stream(stream))
    ctx.writeAndFlush(new DefaultHttp2DataFrame(true).stream(stream))
    ()
  }

  /**
   * Writes any response to the Channel
   */
  def unsafeWriteAnyResponse[A](
    res: Response,
    ctx: ChannelHandlerContext,
    stream: Http2FrameStream,
  ): Unit = {
    val headers = new DefaultHttp2Headers().status(res.status.asJava.codeAsText())
    headers
      .set(HttpHeaderNames.SERVER, "ZIO-Http")
      .set(HttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
    val length  = res.data match {
      case HttpData.Empty               => 0
      case HttpData.Text(text, _)       => text.length
      case HttpData.BinaryChunk(data)   => data.length
      case HttpData.BinaryByteBuf(data) => data.toString(HTTP_CHARSET).length
      case HttpData.BinaryStream(_)     => -1
      case HttpData.File(_)             => 0
    }
    if (length >= 0) headers.setInt(HttpHeaderNames.CONTENT_LENGTH, length)

    ctx.write(
      new DefaultHttp2HeadersFrame(headers).stream(stream),
      ctx.channel().voidPromise(),
    )
    res.data match {
      case HttpData.Empty               =>
        ctx.writeAndFlush(new DefaultHttp2DataFrame(true).stream(stream))
        ()
      case HttpData.Text(text, charset) =>
        ctx.writeAndFlush(
          new DefaultHttp2DataFrame(JUnpooled.copiedBuffer(text, charset)).stream(stream),
        )
        ctx.write(new DefaultHttp2DataFrame(true).stream(stream))
        ()
      case HttpData.BinaryChunk(data)   =>
        ctx.writeAndFlush(
          new DefaultHttp2DataFrame(JUnpooled.copiedBuffer(data.toArray)).stream(stream),
        )
        ctx.write(new DefaultHttp2DataFrame(true).stream(stream))
        ()
      case HttpData.BinaryByteBuf(data) =>
        ctx.writeAndFlush(
          new DefaultHttp2DataFrame(JUnpooled.copiedBuffer(data)).stream(stream),
        )
        ctx.write(new DefaultHttp2DataFrame(true).stream(stream))
        ()
      case HttpData.BinaryStream(data)  =>
        runtime.unsafeRun(ctx) {
          for {
            _ <- data.foreach(c =>
              ChannelFuture.unit(
                ctx.writeAndFlush(
                  new DefaultHttp2DataFrame(JUnpooled.copiedBuffer(c)).stream(stream),
                ),
              ),
            )
            _ <- ChannelFuture.unit(ctx.writeAndFlush(new DefaultHttp2DataFrame(true).stream(stream)))
          } yield ()
        }
      case HttpData.File(file)          =>
        import java.io.RandomAccessFile

        val raf        = new RandomAccessFile(file, "r")
        val fileLength = raf.length()
        // TODO: make sure this works
        // Write the content.
        ctx.write(new DefaultFileRegion(raf.getChannel, 0, fileLength))
        // Write the end marker.
        ctx.write(new DefaultHttp2DataFrame(true).stream(stream))
        ()
    }

  }

}
