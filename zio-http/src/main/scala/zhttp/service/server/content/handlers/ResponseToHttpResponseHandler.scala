package zhttp.service.server.content.handlers

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http._
import zhttp.core.Util
import zhttp.http._
import zhttp.service.server.{ServerTimeGenerator, WebSocketUpgrade}
import zhttp.service.{ChannelFuture, HttpRuntime}
import zio.stream.ZStream
import zio.{Task, UIO, ZIO}

trait ResponseToHttpResponseHandler[R] { self: WebSocketUpgrade[R] =>

  val serverTime: ServerTimeGenerator
  val runtime: HttpRuntime[R]

  /**
   * Executes http apps
   */
  private[zhttp] def unsafeRun[A](
    jReq: FullHttpRequest,
    http: Http[R, Throwable, A, Response[R, Throwable]],
    a: A,
  )(implicit ctx: ChannelHandlerContext): Unit = {
    http.execute(a) match {
      case HExit.Effect(resM) =>
        unsafeRunZIO {
          resM.foldM(
            {
              case Some(cause) =>
                UIO {
                  unsafeWriteAndFlushErrorResponse(cause)
                  releaseRequest(jReq)
                }
              case None        =>
                UIO {
                  unsafeWriteAndFlushEmptyResponse()
                  releaseRequest(jReq)
                }
            },
            res =>
              if (self.isWebSocket(res)) UIO(self.upgradeToWebSocket(ctx, jReq, res))
              else {
                for {
                  _ <- UIO { unsafeWriteAnyResponse(res) }
                  _ <- res.data match {
                    case HttpData.Empty =>
                      UIO {
                        unsafeWriteAndFlushLastEmptyContent()
                      }

                    case data @ HttpData.Text(_, _) =>
                      UIO {
                        unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))
                      }

                    case HttpData.BinaryByteBuf(data) =>
                      UIO {
                        unsafeWriteLastContent(data)
                      }

                    case data @ HttpData.BinaryChunk(_) =>
                      UIO {
                        unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))
                      }

                    case HttpData.BinaryStream(stream) =>
                      writeStreamContent(stream)
                  }
                  _ <- Task(releaseRequest(jReq))
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.isWebSocket(res)) {
          self.upgradeToWebSocket(ctx, jReq, res)
        } else {
          unsafeWriteAnyResponse(res)

          res.data match {
            case HttpData.Empty =>
              unsafeWriteAndFlushLastEmptyContent()
              releaseRequest(jReq)

            case data @ HttpData.Text(_, _) =>
              unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))
              releaseRequest(jReq)

            case HttpData.BinaryByteBuf(data) =>
              unsafeWriteLastContent(data)
              releaseRequest(jReq)

            case data @ HttpData.BinaryChunk(_) =>
              unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))
              releaseRequest(jReq)

            case HttpData.BinaryStream(stream) =>
              unsafeRunZIO(
                writeStreamContent(stream) *> Task(releaseRequest(jReq)),
              )
          }
        }

      case HExit.Failure(e) =>
        unsafeWriteAndFlushErrorResponse(e)
        releaseRequest(jReq)
      case HExit.Empty      =>
        unsafeWriteAndFlushEmptyResponse()
        releaseRequest(jReq)
    }

  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: ChannelHandlerContext): Unit =
    runtime.unsafeRun(ctx) {
      program
    }

  /**
   * Releases the FullHttpRequest safely.
   */
  private def releaseRequest(jReq: FullHttpRequest): Unit              = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }
  private[zhttp] def decodeResponse(res: Response[_, _]): HttpResponse = {
    if (res.attribute.memoize) decodeResponseCached(res) else decodeResponseFresh(res)
  }

  private def decodeResponseCached(res: Response[_, _]): HttpResponse = {
    val cachedResponse = res.cache
    // Update cache if it doesn't exist OR has become stale
    // TODO: add unit tests for server-time
    if (cachedResponse == null || (res.attribute.serverTime && serverTime.canUpdate())) {
      val jRes = decodeResponseFresh(res)
      res.cache = jRes
      jRes
    } else cachedResponse
  }

  private def decodeResponseFresh(res: Response[_, _]): HttpResponse = {
    val jHeaders = res.getHeaders.encode
    if (res.attribute.serverTime) jHeaders.set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
    new DefaultHttpResponse(HttpVersion.HTTP_1_1, res.status.asJava, jHeaders)
  }

  private[zhttp] def notFoundResponse: HttpResponse = {
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, false)
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
    response
  }

  private def serverErrorResponse(cause: Throwable): HttpResponse = {
    val content  = Util.prettyPrintHtml(cause)
    val response = new DefaultFullHttpResponse(
      HTTP_1_1,
      INTERNAL_SERVER_ERROR,
      Unpooled.copiedBuffer(content, HTTP_CHARSET),
    )
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length)
    response
  }

  /**
   * Writes not found error response to the Channel
   */
  private[zhttp] def unsafeWriteAndFlushEmptyResponse()(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(notFoundResponse): Unit
  }

  /**
   * Writes error response to the Channel
   */
  private[zhttp] def unsafeWriteAndFlushErrorResponse(cause: Throwable)(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(serverErrorResponse(cause)): Unit
  }

  /**
   * Writes last empty content to the Channel
   */
  private[zhttp] def unsafeWriteAndFlushLastEmptyContent()(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
  }

  /**
   * Writes any response to the Channel
   */
  private[zhttp] def unsafeWriteAnyResponse[A](
    res: Response[R, Throwable],
  )(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.write(decodeResponse(res)): Unit
  }

  /**
   * Writes ByteBuf data to the Channel
   */
  private[zhttp] def unsafeWriteLastContent[A](data: ByteBuf)(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(new DefaultLastHttpContent(data)): Unit
  }

  /**
   * Writes Binary Stream data to the Channel
   */
  private[zhttp] def writeStreamContent[A](
    stream: ZStream[R, Throwable, ByteBuf],
  )(implicit ctx: ChannelHandlerContext): ZIO[R, Throwable, Unit] = {
    for {
      _ <- stream.foreach(c => UIO(ctx.writeAndFlush(c)))
      _ <- ChannelFuture.unit(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT))
    } yield ()
  }

}
