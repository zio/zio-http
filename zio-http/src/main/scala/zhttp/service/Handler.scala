package zhttp.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._
import zhttp.core.Util
import zhttp.http._
import zhttp.service.server.{ServerTimeGenerator, WebSocketUpgrade}
import zio.stream.ZStream
import zio.{Task, UIO, ZIO}

import java.io.File
import java.net.{InetAddress, InetSocketAddress}
import java.nio.file.{Files, Paths}

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
            case m: InetSocketAddress => Some(m.getAddress)
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

    /**
     * Sets MIME type in the response headers. This is only relevant in case of File transfers as browsers use the MIME
     * type, not the file extension, to determine how to process a URL. <a href="MSDN
     * Doc">https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type</a>
     */
    res.data match {
      case HttpData.File(file) =>
        jHeaders.set(HttpHeaderNames.CONTENT_TYPE, Files.probeContentType(Paths.get(file.toString)))
      case _                   =>
    }
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
                  _ <- UIO {
                    // Write the initial line and the header.
                    unsafeWriteAnyResponse(res)
                  }
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

                    case HttpData.File(file) =>
                      UIO {
                        unsafeWriteFileContent(file)
                      }
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
          // Write the initial line and the header.
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

            case HttpData.File(file) =>
              unsafeWriteFileContent(file)
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

  /**
   * Writes file content to the Channel. Does not use Chunked transfer encoding
   */
  private def unsafeWriteFileContent(file: File)(implicit ctx: ChannelHandlerContext): Unit = {
    import java.io.RandomAccessFile

    val raf        = new RandomAccessFile(file, "r")
    val fileLength = raf.length()
    // Write the content.
    ctx.write(new DefaultFileRegion(raf.getChannel, 0, fileLength))
    // Write the end marker.
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
  }
}
