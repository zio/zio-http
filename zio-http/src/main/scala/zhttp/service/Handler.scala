package zhttp.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.core.Util
import zhttp.http._
import zhttp.service.server.{ServerTimeGenerator, WebSocketUpgrade}
import zio.stream.ZStream
import zio.{Task, UIO, ZIO}

import java.io.File
import java.net.{InetAddress, InetSocketAddress}

@Sharable
private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTime: ServerTimeGenerator,
) extends SimpleChannelInboundHandler[FullHttpRequest](false)
    with WebSocketUpgrade[R] {
  self =>

  type Ctx = ChannelHandlerContext

  override def channelRead0(ctx: Ctx, jReq: FullHttpRequest): Unit = {
    jReq.touch("server.Handler-channelRead0")
    implicit val iCtx: ChannelHandlerContext = ctx
    unsafeRun(
      jReq,
      app,
      new Request {
        override def method: Method = Method.fromHttpMethod(jReq.method())

        override def url: URL = URL.fromString(jReq.uri()).getOrElse(null)

        override def getHeaders: Headers = Headers.make(jReq.headers())

        override private[zhttp] def getBodyAsByteBuf: Task[ByteBuf] = Task(jReq.content())

        override def remoteAddress: Option[InetAddress] = {
          ctx.channel().remoteAddress() match {
            case m: InetSocketAddress => Some(m.getAddress)
            case _                    => None
          }
        }
      },
    )
  }

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }

  /**
   * Checks if an encoded version of the response exists, uses it if it does. Otherwise, it will return a fresh
   * response. It will also set the server time if requested by the client.
   */
  private def encodeResponse(res: Response): HttpResponse = {

    val jResponse = res.attribute.encoded match {

      // Check if the encoded response exists and/or was modified.
      case Some((oRes, jResponse)) if oRes eq res =>
        jResponse match {
          // Duplicate the response without allocating much memory
          case response: FullHttpResponse =>
            response.retainedDuplicate()

          case response =>
            response
        }

      case _ => res.unsafeEncode()
    }
    // Identify if the server time should be set and update if required.
    if (res.attribute.serverTime) jResponse.headers().set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
    jResponse
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
      HttpVersion.HTTP_1_1,
      HttpResponseStatus.INTERNAL_SERVER_ERROR,
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
    http: Http[R, Throwable, A, Response],
    a: A,
  )(implicit ctx: Ctx): Unit = {
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
                    unsafeWriteAndFlushAnyResponse(res)
                  }
                  _ <- res.data match {
                    case HttpData.BinaryStream(stream) => writeStreamContent(stream)
                    case HttpData.File(file)           =>
                      UIO {
                        unsafeWriteFileContent(file)
                      }
                    case _                             => UIO(ctx.flush())
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
          unsafeWriteAndFlushAnyResponse(res)
          res.data match {
            case HttpData.BinaryStream(stream) => unsafeRunZIO(writeStreamContent(stream) *> Task(releaseRequest(jReq)))
            case HttpData.File(file)           =>
              unsafeWriteFileContent(file)
            case _                             => releaseRequest(jReq)
          }
        }
      case HExit.Failure(e)   =>
        unsafeWriteAndFlushErrorResponse(e)
        releaseRequest(jReq)
      case HExit.Empty        =>
        unsafeWriteAndFlushEmptyResponse()
        releaseRequest(jReq)
    }
  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: Ctx): Unit =
    runtime.unsafeRun(ctx) {
      program
    }

  /**
   * Writes any response to the Channel
   */
  private def unsafeWriteAndFlushAnyResponse[A](res: Response)(implicit ctx: Ctx): Unit = {
    ctx.writeAndFlush(encodeResponse(res)): Unit
  }

  /**
   * Writes not found error response to the Channel
   */
  private def unsafeWriteAndFlushEmptyResponse()(implicit ctx: Ctx): Unit = {
    ctx.writeAndFlush(notFoundResponse): Unit
  }

  /**
   * Writes error response to the Channel
   */
  private def unsafeWriteAndFlushErrorResponse(cause: Throwable)(implicit ctx: Ctx): Unit = {
    ctx.writeAndFlush(serverErrorResponse(cause)): Unit
  }

  /**
   * Writes Binary Stream data to the Channel
   */
  private def writeStreamContent(
    stream: ZStream[Any, Throwable, ByteBuf],
  )(implicit ctx: Ctx): ZIO[Any, Throwable, Unit] = {
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
