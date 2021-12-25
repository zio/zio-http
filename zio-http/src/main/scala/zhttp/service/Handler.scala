package zhttp.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._
import zhttp.core.Util
import zhttp.http._
import zhttp.service.server.{ServerTimeGenerator, WebSocketUpgrade}
import zio.stream.ZStream
import zio.{Task, UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}

@Sharable
private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTime: ServerTimeGenerator,
) extends SimpleChannelInboundHandler[FullHttpRequest](false)
    with WebSocketUpgrade[R] { self =>

  override def channelRead0(ctx: ChannelHandlerContext, jReq: FullHttpRequest): Unit = {
    jReq.touch("server.Handler-channelRead0")
    implicit val iCtx: ChannelHandlerContext = ctx
    unsafeRun(
      jReq,
      app,
      new Request {
        override def method: Method                                 = Method.fromHttpMethod(jReq.method())
        override def url: URL                                       = URL.fromString(jReq.uri()).getOrElse(null)
        override def getHeaders: Headers                            = Headers.make(jReq.headers())
        override private[zhttp] def getBodyAsByteBuf: Task[ByteBuf] = Task(jReq.content())
        override def remoteAddress: Option[InetAddress]             = {
          ctx.channel().remoteAddress() match {
            case m: InetSocketAddress => Some(m.getAddress())
            case _                    => None
          }
        }
      },
    )
  }

  private def decodeResponse(res: Response[_, _]): HttpResponse = {
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

  private def notFoundResponse: HttpResponse = {
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, false)
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
    response
  }

  /**
   * Releases the FullHttpRequest safely.
   */
  private def releaseRequest(jReq: FullHttpRequest): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
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
   * Executes http apps
   */
  private def unsafeRun[A](
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
                  processErrorResponse(jReq, Some(cause))
                }
              case None        =>
                UIO {
                  processErrorResponse(jReq, None)
                }
            },
            res =>
              UIO {
                processResponse(jReq, res)
              },
          )
        }

      case HExit.Success(res) =>
        processResponse(jReq, res)
      case HExit.Failure(e)   =>
        processErrorResponse(jReq, Some(e))
      case HExit.Empty        =>
        processErrorResponse(jReq, None)
    }

  }

  private def processResponse(jReq: FullHttpRequest, res: Response[R, Throwable])(implicit
    ctx: ChannelHandlerContext,
  ) = {
    if (self.isWebSocket(res)) {
      self.upgradeToWebSocket(ctx, jReq, res)
    } else {
      unsafeWriteAnyResponse(res)

      res.data match {
        case HttpData.Empty =>
          unsafeWriteAndFlushLastEmptyContent()

        case data @ HttpData.Text(_, _) =>
          unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))

        case HttpData.Complete(data) =>
          unsafeWriteLastContent(data)

        case data @ HttpData.BinaryChunk(_) =>
          unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))

        case HttpData.BinaryStream(stream) =>
          unsafeRunZIO(
            writeStreamContent(stream),
          )
      }
      releaseRequest(jReq)
    }
  }

  private def processErrorResponse(jReq: FullHttpRequest, err: Option[Throwable])(implicit
    ctx: ChannelHandlerContext,
  ) = {
    err.fold(unsafeWriteAndFlushEmptyResponse())(unsafeWriteAndFlushErrorResponse)
    releaseRequest(jReq)
  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: ChannelHandlerContext): Unit =
    runtime.unsafeRun(ctx) {
      program
    }

  /**
   * Writes not found error response to the Channel
   */
  private def unsafeWriteAndFlushEmptyResponse()(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(notFoundResponse): Unit
  }

  /**
   * Writes error response to the Channel
   */
  private def unsafeWriteAndFlushErrorResponse(cause: Throwable)(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(serverErrorResponse(cause)): Unit
  }

  /**
   * Writes last empty content to the Channel
   */
  private def unsafeWriteAndFlushLastEmptyContent()(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
  }

  /**
   * Writes any response to the Channel
   */
  private def unsafeWriteAnyResponse[A](res: Response[R, Throwable])(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.write(decodeResponse(res)): Unit
  }

  /**
   * Writes ByteBuf data to the Channel
   */
  private def unsafeWriteLastContent[A](data: ByteBuf)(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(new DefaultLastHttpContent(data)): Unit
  }

  /**
   * Writes Binary Stream data to the Channel
   */
  private def writeStreamContent[A](
    stream: ZStream[R, Throwable, ByteBuf],
  )(implicit ctx: ChannelHandlerContext): ZIO[R, Throwable, Unit] = {
    for {
      _ <- stream.foreach(c => UIO(ctx.writeAndFlush(c)))
      _ <- ChannelFuture.unit(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT))
    } yield ()
  }
}
