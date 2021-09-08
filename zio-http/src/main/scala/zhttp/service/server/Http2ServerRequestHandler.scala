package zhttp.service.server

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.unix.Errors.NativeIoException
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext}
import io.netty.handler.codec.http.{HttpHeaderNames, HttpResponseStatus}
import io.netty.handler.codec.http2.{
  DefaultHttp2DataFrame,
  DefaultHttp2Headers,
  DefaultHttp2HeadersFrame,
  Http2HeadersFrame,
}
import zhttp.http.{HttpError, HttpResult, Response, SilentResponse, _}
import zhttp.service.Server.Settings
import zhttp.service._
import zio.{Chunk, Exit}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable.Map

@Sharable
final case class Http2ServerRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  settings: Settings[R, Throwable],
) extends ChannelDuplexHandler
    with HttpMessageCodec {
  val hedaerMap: Map[Int, Http2HeadersFrame]         = Map.empty[Int, Http2HeadersFrame]
  val dataMap: Map[Int, List[DefaultHttp2DataFrame]] = Map.empty[Int, List[DefaultHttp2DataFrame]]

  @throws[Exception]
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    settings.error match {
      case Some(v) => zExec.unsafeExecute_(ctx)(v(cause).uninterruptible)
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
  ): Unit =
    executeAsync(ctx, headers, dataL) {
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
                      new DefaultHttp2DataFrame(JUnpooled.copiedBuffer(c.toArray)).stream(headers.stream()),
                    ),
                  ),
                )
                _ <- ChannelFuture.unit(ctx.writeAndFlush(new DefaultHttp2DataFrame(true).stream(headers.stream())))
              } yield ()
            }
          case HttpData.CompleteData(data) =>
            ctx.writeAndFlush(
              new DefaultHttp2DataFrame(JUnpooled.copiedBuffer(data.toArray)).stream(headers.stream()),
            )
            ctx.writeAndFlush(new DefaultHttp2DataFrame(true).stream(headers.stream()))
            ()
          case HttpData.Empty              =>
            ctx.writeAndFlush(new DefaultHttp2DataFrame(true).stream(headers.stream()))
            ()
        }

      case _ @Response.SocketResponse(_) => {
        val c   = Chunk.fromArray(
          "Websockets are not supported over HTTP/2. Make HTTP/1.1 connection.".getBytes(HTTP_CHARSET),
        )
        val hhh = new DefaultHttp2Headers().status(HttpResponseStatus.UPGRADE_REQUIRED.codeAsText())
        hhh
          .set(HttpHeaderNames.SERVER, "ZIO-Http")
          .set(HttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
          .setInt(HttpHeaderNames.CONTENT_LENGTH, c.length)
        ctx.write(
          new DefaultHttp2HeadersFrame(hhh).stream(headers.stream()),
          ctx.channel().voidPromise(),
        )

        ctx.writeAndFlush(
          new DefaultHttp2DataFrame(JUnpooled.copiedBuffer(c.toArray)).stream(headers.stream()),
        )
        ctx.write(new DefaultHttp2DataFrame(true).stream(headers.stream()))
        ()
      }
    }

  private def executeAsync(ctx: ChannelHandlerContext, hh: Http2HeadersFrame, dataL: List[DefaultHttp2DataFrame])(
    cb: Response[R, Throwable] => Unit,
  ): Unit =
    decodeHttp2Header(hh, ctx, dataL) match {
      case Left(err)  => cb(err.toResponse)
      case Right(req) => {
        settings.http.execute(req).evaluate match {
          case HttpResult.Empty => cb(Response.fromHttpError(HttpError.NotFound(Path(hh.headers().path().toString))))
          case HttpResult.Success(a) => cb(a)
          case HttpResult.Failure(e) => cb(SilentResponse[Throwable].silent(e))
          case HttpResult.Effect(z)  =>
            zExec.unsafeExecute(ctx, z) {
              case Exit.Success(res)   => cb(res)
              case Exit.Failure(cause) =>
                cause.failureOption match {
                  case Some(Some(e)) => cb(SilentResponse[Throwable].silent(e))
                  case Some(None) => cb(Response.fromHttpError(HttpError.NotFound(Path(hh.headers().path().toString))))
                  case None       => {
                    ctx.close()
                    ()
                  }
                }
            }
        }
      }
    }
}
