package zhttp.service

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.core.Util
import zhttp.http._
import zhttp.service.server.{ContentDecoder, ServerTimeGenerator, WebSocketUpgrade}
import zio.stream.ZStream
import zio.{Chunk, Promise, Task, UIO, ZIO}

import java.io.File
import java.net.{InetAddress, InetSocketAddress}

@Sharable
private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTime: ServerTimeGenerator,
) extends SimpleChannelInboundHandler[Any](false)
    with WebSocketUpgrade[R] {
  self =>

  type Ctx = ChannelHandlerContext

  import io.netty.util.AttributeKey

  private val DECODER_KEY: AttributeKey[ContentDecoder[Any, Throwable, Chunk[Byte], Any]] =
    AttributeKey.valueOf("decoderKey")
  private val COMPLETE_PROMISE: AttributeKey[Promise[Throwable, Any]]                     =
    AttributeKey.valueOf("completePromise")
  private val isFirst: AttributeKey[Boolean]                                              =
    AttributeKey.valueOf("isFirst")
  private val decoderState: AttributeKey[Any]                                             =
    AttributeKey.valueOf("decoderState")
  val jRequest: AttributeKey[HttpRequest]                                                 = AttributeKey.valueOf("jReq")
  private val request: AttributeKey[Request] = AttributeKey.valueOf("request")
  private val cBody: AttributeKey[ByteBuf]   = AttributeKey.valueOf("cbody")

  override def channelRead0(ctx: Ctx, msg: Any): Unit = {
    implicit val iCtx: ChannelHandlerContext = ctx
    msg match {
      case jReq: HttpRequest    =>
        ctx.channel().config().setAutoRead(false)
        ctx.channel().attr(jRequest).set(jReq)

        val newRequest = new Request {
          override def method: Method                                 = Method.fromHttpMethod(jReq.method())
          override def url: URL                                       = URL.fromString(jReq.uri()).getOrElse(null)
          override def getHeaders: Headers                            = Headers.make(jReq.headers())
          override private[zhttp] def getBodyAsByteBuf: Task[ByteBuf] = ???

          override def decodeContent[R0, B](
            decoder: ContentDecoder[R0, Throwable, Chunk[Byte], B],
          ): ZIO[R0, Throwable, B] =
            ZIO.effectSuspendTotal {
              if (
                ctx
                  .channel()
                  .attr(DECODER_KEY)
                  .get() != null
              )
                ZIO.fail(ContentDecoder.Error.ContentDecodedOnce)
              else
                for {
                  p <- Promise.make[Throwable, B]
                  _ <- UIO {
                    ctx
                      .channel()
                      .attr(DECODER_KEY)
                      .setIfAbsent(decoder.asInstanceOf[ContentDecoder[Any, Throwable, Chunk[Byte], Any]])
                      .asInstanceOf[ContentDecoder[Any, Throwable, Chunk[Byte], B]]
                    ctx.channel().attr(COMPLETE_PROMISE).set(p.asInstanceOf[Promise[Throwable, Any]])
                    ctx.read(): Unit
                  }
                  b <- p.await
                } yield b
            }

          override def remoteAddress: Option[InetAddress] = {
            ctx.channel().remoteAddress() match {
              case m: InetSocketAddress => Some(m.getAddress)
              case _                    => None
            }
          }
        }
        ctx.channel().attr(request).set(newRequest)
        unsafeRun(
          jReq,
          app,
          newRequest,
        )
      case msg: LastHttpContent =>
        decodeContent(msg.content(), ctx.channel().attr(DECODER_KEY).get(), true)
      case msg: HttpContent     => decodeContent(msg.content(), ctx.channel().attr(DECODER_KEY).get(), false)
      case _                    => ???
    }

  }

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }

  /**
   * Checks if an encoded version of the response exists, uses it if it does. Otherwise, it will return a fresh
   * response. It will also set the server time if requested by the client.
   */
  private def encodeResponse(res: Response[_, _]): HttpResponse = {

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
  def releaseRequest(jReq: FullHttpRequest): Unit = {
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
    jReq: HttpRequest,
    http: Http[R, Throwable, A, Response[R, Throwable]],
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
                  // releaseRequest(jReq)
                }
              case None        =>
                UIO {
                  unsafeWriteAndFlushEmptyResponse()
                  // releaseRequest(jReq)
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
                  //  _ <- Task(releaseRequest(jReq))
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
            case HttpData.BinaryStream(stream) => unsafeRunZIO(writeStreamContent(stream))
            case HttpData.File(file)           =>
              unsafeWriteFileContent(file)
            case _                             => ()
          }
        }
      case HExit.Failure(e)   =>
        unsafeWriteAndFlushErrorResponse(e)
      // releaseRequest(jReq)
      case HExit.Empty        =>
        unsafeWriteAndFlushEmptyResponse()
      // releaseRequest(jReq)
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
  private def unsafeWriteAndFlushAnyResponse[A](res: Response[R, Throwable])(implicit ctx: Ctx): Unit = {
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
   * Decodes content and executes according to the ContentDecoder provided
   */
  private def decodeContent(
    content: ByteBuf,
    decoder: ContentDecoder[Any, Throwable, Chunk[Byte], Any],
    isLast: Boolean,
  )(implicit ctx: ChannelHandlerContext): Unit = {
    decoder match {
      case ContentDecoder.Text =>
        if (ctx.channel().attr(cBody).get() != null) {
          ctx.channel().attr(cBody).get().writeBytes(content)
        } else {
          ctx.channel().attr(cBody).set(Unpooled.compositeBuffer().writeBytes(content))
        }

        if (isLast) {
          unsafeRunZIO(
            ctx.channel().attr(COMPLETE_PROMISE).get().succeed(ctx.channel().attr(cBody).get().toString(HTTP_CHARSET)),
          )
        } else {
          ctx.read(): Unit
        }

      case step: ContentDecoder.Step[_, _, _, _, _] =>
        println(s"here: $isLast")
        if (!ctx.channel().attr(isFirst).get()) {
          println(s"first: ${step.state}")
          ctx.channel().attr(decoderState).set(step.state)
          ctx.channel().attr(isFirst).set(true)
        }

        unsafeRunZIO(for {
          (publish, state) <- step
            .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], Any]]
            .next(
              // content.array() fails with post request with body
              // Link: https://livebook.manning.com/book/netty-in-action/chapter-5/54
              Chunk.fromArray(ByteBufUtil.getBytes(content)),
              ctx.channel().attr(decoderState).get(),
              isLast,
              ctx.channel().attr(request).get().method,
              ctx.channel().attr(request).get().url,
              ctx.channel().attr(request).get().getHeaders,
            )
          _                <- publish match {
            case Some(out) => ctx.channel().attr(COMPLETE_PROMISE).get().succeed(out)
            case None      => ZIO.unit
          }
          _                <- UIO {
            ctx.channel().attr(decoderState).set(state)
            if (!isLast) {
              ctx.read()
              println("next")
            }
          }
        } yield ())

    }
  }

  /**
   * Writes Binary Stream data to the Channel
   */
  private def writeStreamContent[A](
    stream: ZStream[R, Throwable, ByteBuf],
  )(implicit ctx: Ctx): ZIO[R, Throwable, Unit] = {
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
