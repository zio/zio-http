package zhttp.service.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelDuplexHandler, ChannelHandlerContext}
import io.netty.handler.codec.http2._
import zhttp.http.Status.{NOT_FOUND, UPGRADE_REQUIRED}
import zhttp.http._
import zhttp.service.Server.Config
import zhttp.service._
import zio.{UIO, ZIO}

import scala.collection.mutable.Map

@Sharable
final case class Http2ServerRequestHandler[R] private[zhttp] (
                                                               runtime: HttpRuntime[R],
                                                               config: Config[R, Throwable],
) extends ChannelDuplexHandler
    with HttpMessageCodec
    with WebSocketUpgrade[R] { self =>
  val hedaerMap: Map[Int, Http2HeadersFrame]         = Map.empty[Int, Http2HeadersFrame]
  val dataMap: Map[Int, List[DefaultHttp2DataFrame]] = Map.empty[Int, List[DefaultHttp2DataFrame]]

  @throws[Exception]
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }

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
    val stream = headers.stream()
    decodeHttp2Header(headers, ctx, dataL) match {
      case Left(cause)   => ctx.fireChannelRead((Response.fromHttpError(HttpError.InternalServerError(cause = Some(cause))),stream))
      case Right(jReq) => {
        config.app.execute(jReq) match {
          case HExit.Failure(e)   => ctx.fireChannelRead((Response.fromHttpError(HttpError.InternalServerError(cause = Some(e))),stream))
          case HExit.Empty        => ctx.fireChannelRead((Response.status(NOT_FOUND),stream))
          case HExit.Success(res) =>
            if (self.isWebSocket(res)) {
              ctx.fireChannelRead(
                (Response(
                  UPGRADE_REQUIRED,
                  data = HttpData.fromString("Websockets are not supported over HTTP/2. Make HTTP/1.1 connection."),
                ),
                stream,)
              )
            } else {
              ctx.fireChannelRead((res, stream))
            }
          case HExit.Effect(resM) =>
            unsafeRunZIO(
              resM.foldM(
                {
                  case Some(cause) => UIO(ctx.fireChannelRead((Response.fromHttpError(HttpError.InternalServerError(cause = Some(cause))), stream)))
                  case None        => UIO(ctx.fireChannelRead((Response.status(NOT_FOUND),stream)))
                },
                res =>
                  if (self.isWebSocket(res))
                    UIO(
                      ctx.fireChannelRead(
                        (Response(
                          UPGRADE_REQUIRED,
                          data = HttpData.fromString("Websockets are not supported over HTTP/2. Make HTTP/1.1 connection."),
                        ),
                          stream,)
                      )
                    )
                  else {
                    for {
                      _ <- UIO(ctx.fireChannelRead((res, stream)))
                    } yield ()
                  },
              ),
              ctx,
            )
        }
      }
    }
  }


}
