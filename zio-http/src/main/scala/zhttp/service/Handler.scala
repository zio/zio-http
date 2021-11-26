package zhttp.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.server.{ServerTimeGenerator, WebSocketUpgrade}
import zio.stream.ZStream
import zio.{Chunk, Task, UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}

private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTime: ServerTimeGenerator,
) extends SimpleChannelInboundHandler[FullHttpRequest](false)
    with WebSocketUpgrade[R] { self =>

  override def channelRead0(ctx: ChannelHandlerContext, jReq: FullHttpRequest): Unit = {
    implicit val iCtx: ChannelHandlerContext = ctx
    unsafeRun(
      jReq,
      app,
      new Request {
        override def method: Method                                 = Method.fromHttpMethod(jReq.method())
        override def url: URL                                       = URL.fromString(jReq.uri()).getOrElse(null)
        override def getHeaders: List[Header]                       = Header.make(jReq.headers())
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

  private val notFoundResponse: HttpResponse = {
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, false)
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
    response
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
    val jHeaders = Header.disassemble(res.getHeaders)
    if (res.attribute.serverTime) jHeaders.set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
    new DefaultHttpResponse(HttpVersion.HTTP_1_1, res.status.asJava, jHeaders)
  }

  /**
   * Releases the FullHttpRequest safely.
   */
  private def releaseRequest(jReq: FullHttpRequest): Unit = {
    jReq.release(jReq.refCnt()): Unit
  }

  private def serverErrorResponse(cause: Throwable): HttpResponse = {
    val content  = cause.toString
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
    http.execute(a).evaluate match {
      case HExit.Effect(resM) =>
        unsafeRunZIO {
          resM.foldM(
            {
              case Some(cause) => UIO(unsafeWriteAndFlushErrorResponse(cause))
              case None        => UIO(unsafeWriteAndFlushEmptyResponse())
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
                        releaseRequest(jReq)
                      }

                    case data @ HttpData.Text(_, _) =>
                      UIO(unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize)))

                    case HttpData.BinaryByteBuf(data) => UIO(unsafeWriteLastContent(data))

                    case data @ HttpData.BinaryChunk(_) =>
                      UIO(unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize)))

                    case HttpData.BinaryStream(stream) =>
                      writeStreamContent(stream.mapChunks(a => Chunk(Unpooled.copiedBuffer(a.toArray))))
                  }
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.isWebSocket(res)) {
          self.upgradeToWebSocket(ctx, jReq, res)
        } else {
          unsafeWriteAnyResponse(res)
          releaseRequest(jReq)

          res.data match {
            case HttpData.Empty =>
              unsafeWriteAndFlushLastEmptyContent()

            case data @ HttpData.Text(_, _) =>
              unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))

            case HttpData.BinaryByteBuf(data) =>
              unsafeWriteLastContent(data)

            case data @ HttpData.BinaryChunk(_) =>
              unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))

            case HttpData.BinaryStream(stream) =>
              unsafeRunZIO(writeStreamContent(stream.mapChunks(a => Chunk(Unpooled.copiedBuffer(a.toArray)))))
          }
        }

      case HExit.Failure(e) => unsafeWriteAndFlushErrorResponse(e)
      case HExit.Empty      => unsafeWriteAndFlushEmptyResponse()
    }
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
  private def writeStreamContent[A](stream: ZStream[R, Throwable, ByteBuf])(implicit ctx: ChannelHandlerContext) = {
    stream.process.map { pull =>
      def loop: ZIO[R, Throwable, Unit] = pull
        .foldM(
          {
            case None        => UIO(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)).unit
            case Some(error) => ZIO.fail(error)
          },
          chunks =>
            for {
              _ <- ZIO.foreach_(chunks)(buf => UIO(ctx.write(new DefaultHttpContent(buf))))
              _ <- UIO(ctx.flush())
              _ <- loop
            } yield (),
        )

      loop
    }.useNow.flatten
  }
}
