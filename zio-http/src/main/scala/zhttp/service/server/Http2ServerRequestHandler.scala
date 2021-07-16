package zhttp.service.server

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.channel.unix.Errors.NativeIoException
import io.netty.channel.{ChannelDuplexHandler => JChannelDuplexHandler}
import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames, HttpResponseStatus => JHttpResponseStatus}
import io.netty.handler.codec.http2.{
  DefaultHttp2DataFrame => JDefaultHttp2DataFrame,
  DefaultHttp2Headers => JDefaultHttp2Headers,
  DefaultHttp2HeadersFrame => JDefaultHttp2HeadersFrame,
  Http2HeadersFrame => JHttp2HeadersFrame,
}
import zhttp.core.{JChannelHandlerContext, JSharable}
import zhttp.http.{HttpError, HttpResult, Response, SilentResponse, _}
import zhttp.service.Server.Settings
import zhttp.service._
import zio.{Chunk, Exit}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable.Map

@JSharable
final case class Http2ServerRequestHandler[R](
  zExec: UnsafeChannelExecutor[R],
  settings: Settings[R, Throwable],
) extends JChannelDuplexHandler
    with HttpMessageCodec {
  val hedaerMap: Map[Int, JHttp2HeadersFrame]         = Map.empty[Int, JHttp2HeadersFrame]
  val dataMap: Map[Int, List[JDefaultHttp2DataFrame]] = Map.empty[Int, List[JDefaultHttp2DataFrame]]

  @throws[Exception]
  override def exceptionCaught(ctx: JChannelHandlerContext, cause: Throwable): Unit = {
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
  override def channelRead(ctx: JChannelHandlerContext, msg: Any): Unit = {
    if (msg.isInstanceOf[JHttp2HeadersFrame]) {
      onHeaderRead(ctx, msg.asInstanceOf[JHttp2HeadersFrame])
    } else if (msg.isInstanceOf[JDefaultHttp2DataFrame]) {
      onDataRead(ctx, msg.asInstanceOf[JDefaultHttp2DataFrame])
    } else super.channelRead(ctx, msg)
    ()
  }

  private def onHeaderRead(ctx: JChannelHandlerContext, header: JHttp2HeadersFrame): Unit = {
    if (header.isEndStream) {
      onEndStream(ctx, header)
    } else {
      hedaerMap.put(header.stream().id(), header)
      ()
    }
  }

  private def onDataRead(ctx: JChannelHandlerContext, data: JDefaultHttp2DataFrame) = {
    val stream = data.stream().id()
    if (data.isEndStream) {
      if (hedaerMap.contains(stream)) {
        val header = hedaerMap.get(stream).get
        if (dataMap.contains(stream)) {
          onEndStream(ctx, header, dataMap.get(stream).get.appended(data))
        } else {
          onEndStream(ctx, header, List(data))
        }
      } else {
        //TODO: say that there is no header
      }
    } else {
      if (dataMap.contains(stream)) {
        dataMap.update(stream, dataMap.get(stream).get.appended(data))
      } else {
        dataMap.put(stream, List(data))
      }
    }
  }

  @throws[Exception]
  override def channelReadComplete(ctx: JChannelHandlerContext): Unit = {
    ctx.flush
    ()
  }

  @throws[Exception]
  private def onEndStream(
    ctx: JChannelHandlerContext,
    headers: JHttp2HeadersFrame,
    dataL: List[JDefaultHttp2DataFrame] = null,
  ): Unit =
    executeAsync(ctx, headers, dataL) {
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
            ctx.writeAndFlush(
              new JDefaultHttp2DataFrame(JUnpooled.copiedBuffer(data.toArray)).stream(headers.stream()),
            )
            ctx.write(new JDefaultHttp2DataFrame(true).stream(headers.stream()))
            ()
          case HttpData.Empty              =>
            ctx.write(new JDefaultHttp2DataFrame(true).stream(headers.stream()))
            ()
        }

      case _ @Response.SocketResponse(_) => {
        val c   = Chunk.fromArray(
          "Websockets are not supported over HTTP/2. Make HTTP/1.1 connection.".getBytes(HTTP_CHARSET),
        )
        val hhh = new JDefaultHttp2Headers().status(JHttpResponseStatus.UPGRADE_REQUIRED.codeAsText())
        hhh
          .set(JHttpHeaderNames.SERVER, "ZIO-Http")
          .set(JHttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
          .setInt(JHttpHeaderNames.CONTENT_LENGTH, c.length)
        ctx.write(
          new JDefaultHttp2HeadersFrame(hhh).stream(headers.stream()),
          ctx.channel().voidPromise(),
        )

        ctx.writeAndFlush(
          new JDefaultHttp2DataFrame(JUnpooled.copiedBuffer(c.toArray)).stream(headers.stream()),
        )
        ctx.write(new JDefaultHttp2DataFrame(true).stream(headers.stream()))
        ()
      }
    }
  ()

  private def executeAsync(ctx: JChannelHandlerContext, hh: JHttp2HeadersFrame, dataL: List[JDefaultHttp2DataFrame])(
    cb: Response[R, Throwable] => Unit,
  ): Unit =
    decodeHttp2Header(hh, ctx, dataL) match {
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
