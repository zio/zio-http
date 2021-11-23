package zhttp.service

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._
import zhttp.http.HttpApp.InvalidMessage
import zhttp.http._
import zhttp.service.server.{ServerTimeGenerator, WebSocketUpgrade}
import zio.stream.ZStream
import zio.{Chunk, Promise, UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}
import scala.annotation.unused

private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTime: ServerTimeGenerator,
) extends ChannelInboundHandlerAdapter
    with WebSocketUpgrade[R] { self =>

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    implicit val iCtx: ChannelHandlerContext = ctx
    msg match {
      case jRequest: HttpRequest =>
        // TODO: Unnecessary requirement
        // `autoRead` is set when the channel is registered in the event loop.
        // The explicit call here is added to make unit tests work properly
        ctx.channel().config().setAutoRead(false)
        self.jReq = jRequest
        self.request = new Request {
          override def getBody[R0, B](
            decoder: ContentDecoder[R0, Throwable, Chunk[Byte], B],
          ): ZIO[R0, Throwable, B] =
            ZIO.effectSuspendTotal {
              if (self.decoder != null)
                ZIO.fail(ContentDecoder.Error.ContentDecodedOnce)
              else
                for {
                  p <- Promise.make[Throwable, B]
                  _ <- UIO {
                    self.decoder = decoder
                      .asInstanceOf[ContentDecoder[Any, Throwable, Chunk[Byte], B]]
                    self.completePromise = p.asInstanceOf[Promise[Throwable, Any]]
                    ctx.read(): Unit
                  }
                  b <- p.await
                } yield b
            }

          override def method: Method                     = Method.fromHttpMethod(jRequest.method())
          override def url: URL                           = URL.fromString(jRequest.uri()).getOrElse(null)
          override def getHeaders: List[Header]           = Header.make(jRequest.headers())
          override def remoteAddress: Option[InetAddress] = {
            ctx.channel().remoteAddress() match {
              case m: InetSocketAddress => Some(m.getAddress())
              case _                    => None
            }
          }
        }
        unsafeRun(app, self.request)

      case msg: LastHttpContent =>
        if (self.isInitialized) {
          self.switchProtocol(ctx, jReq)
        } else if (decoder != null) {
          decodeContent(msg.content(), decoder, true)
        }

        // TODO: add unit tests
        // Channels are reused when keep-alive header is set.
        // So auto-read needs to be set to true once the first request is processed.
        ctx.channel().config().setAutoRead(true): Unit

      case msg: HttpContent =>
        if (decoder != null) {
          decodeContent(msg.content(), decoder, false)
        }

      case msg => ctx.fireExceptionCaught(InvalidMessage(msg)): Unit
    }
  }

  private val cBody: ByteBuf                                            = Unpooled.compositeBuffer()
  private val notFoundResponse: HttpResponse                            = {
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, false)
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
    response
  }
  private var decoder: ContentDecoder[Any, Throwable, Chunk[Byte], Any] = _
  private var completePromise: Promise[Throwable, Any]                  = _
  private var isFirst: Boolean                                          = true
  private var decoderState: Any                                         = _
  private var jReq: HttpRequest                                         = _
  private var request: Request                                          = _

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
        cBody.writeBytes(content)
        if (isLast) {
          unsafeRunZIO(self.completePromise.succeed(cBody.toString(HTTP_CHARSET)))
        } else {
          ctx.read(): Unit
        }

      case step: ContentDecoder.Step[_, _, _, _, _] =>
        if (self.isFirst) {
          self.decoderState = step.state
          self.isFirst = false
        }
        val nState = self.decoderState

        unsafeRunZIO(for {
          (publish, state) <- step
            .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], Any]]
            .next(
              // content.array() fails with post request with body
              // Link: https://livebook.manning.com/book/netty-in-action/chapter-5/54
              Chunk.fromArray(ByteBufUtil.getBytes(content)),
              nState,
              isLast,
              self.request.method,
              self.request.url,
              self.request.getHeaders,
            )
          _                <- publish match {
            case Some(out) => self.completePromise.succeed(out)
            case None      => ZIO.unit
          }
          _                <- UIO {
            self.decoderState = state
            if (!isLast) ctx.read(): Unit
          }
        } yield ())

    }
  }

  private def decodeResponse(res: Response[_, _]): HttpResponse = {
    if (res.attribute.memoize) decodeResponseCached(res) else decodeResponseFresh(res)
  }

  @unused
  private def decodeResponse(res: Response[_, _], data: ByteBuf): HttpResponse = {
    if (res.attribute.memoize) decodeResponseCached(res, data) else decodeResponseFresh(res, data)
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

  @unused
  private def decodeResponseCached(res: Response[_, _], data: ByteBuf): HttpResponse = {
    val cachedResponse = res.cache
    // Update cache if it doesn't exist OR has become stale
    // TODO: add unit tests for server-time
    if (cachedResponse == null || (res.attribute.serverTime && serverTime.canUpdate())) {
      val jRes = decodeResponseFresh(res, data)
      res.cache = jRes
      jRes
    } else cachedResponse
  }

  private def decodeResponseFresh(res: Response[_, _]): HttpResponse = {
    val jHeaders = Header.disassemble(res.getHeaders)
    if (res.attribute.serverTime) jHeaders.set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
    new DefaultHttpResponse(HttpVersion.HTTP_1_1, res.status.asJava, jHeaders)
  }

  @unused
  private def decodeResponseFresh(res: Response[_, _], data: ByteBuf): HttpResponse = {
    val jHeaders = Header.disassemble(res.getHeaders)
    val trailingHeaders = new DefaultHttpHeaders(false)
    if (res.attribute.serverTime) jHeaders.set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, res.status.asJava, data, jHeaders, trailingHeaders)
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
              if (self.canSwitchProtocol(res)) UIO(self.initializeSwitch(ctx, res))
              else {
                for {
                  _ <- res.data match {
                    case HttpData.Empty =>
                      UIO(unsafeWriteFullResponse(res, Unpooled.EMPTY_BUFFER))

                    case data @ HttpData.Text(_, _) =>
                      UIO(unsafeWriteFullResponse(res, data.encodeAndCache(res.attribute.memoize)))

                    case HttpData.BinaryByteBuf(data) => UIO(unsafeWriteFullResponse(res, data))

                    case data @ HttpData.BinaryChunk(_) =>
                      UIO(unsafeWriteFullResponse(res, data.encodeAndCache(res.attribute.memoize)))

                    case HttpData.BinaryStream(stream) =>
                      UIO(unsafeWriteAnyResponse(res)) *>
                      writeStreamContent(stream.mapChunks(a => Chunk(Unpooled.copiedBuffer(a.toArray))))
                  }
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.canSwitchProtocol(res)) {
          self.initializeSwitch(ctx, res)
        } else {
         // unsafeWriteAnyResponse(res)

          res.data match {
            case HttpData.Empty =>
              unsafeWriteFullResponse(res, Unpooled.EMPTY_BUFFER)

            case data @ HttpData.Text(_, _) =>
              unsafeWriteFullResponse(res, data.encodeAndCache(res.attribute.memoize))

            case HttpData.BinaryByteBuf(data) =>
              unsafeWriteFullResponse(res, data)

            case data @ HttpData.BinaryChunk(_) =>
              unsafeWriteFullResponse(res, data.encodeAndCache(res.attribute.memoize))

            case HttpData.BinaryStream(stream) =>
              unsafeWriteAnyResponse(res)
              unsafeRunZIO(writeStreamContent(stream.mapChunks(a => Chunk(Unpooled.copiedBuffer(a.toArray)))))
          }
        }

      case HExit.Failure(e) => unsafeWriteAndFlushErrorResponse(e)
      case HExit.Empty      => unsafeWriteAndFlushEmptyResponse()
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
    ctx.writeAndFlush(notFoundResponse, ctx.voidPromise()): Unit
  }

  /**
   * Writes error response to the Channel
   */
  private def unsafeWriteAndFlushErrorResponse(cause: Throwable)(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(serverErrorResponse(cause), ctx.voidPromise()): Unit
  }

  /**
   * Writes any response to the Channel
   */
  private def unsafeWriteAnyResponse[A](res: Response[R, Throwable])(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.write(decodeResponse(res), ctx.voidPromise()): Unit
  }

  /**
   * Writes full response to the Channel
   */
  @unused
  private def unsafeWriteFullResponse[A](res: Response[R, Throwable], data: ByteBuf)(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(decodeResponse(res, data), ctx.voidPromise()): Unit
  }

  /**
   * Writes Binary Stream data to the Channel
   */
  private def writeStreamContent[A](stream: ZStream[R, Throwable, ByteBuf])(implicit ctx: ChannelHandlerContext) = {
    stream.process.map { pull =>
      def loop: ZIO[R, Throwable, Unit] = pull
        .foldM(
          {
            case None        => UIO(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, ctx.voidPromise())).unit
            case Some(error) => ZIO.fail(error)
          },
          chunks =>
            for {
              _ <- ZIO.foreach_(chunks)(buf => UIO(ctx.write(new DefaultHttpContent(buf), ctx.voidPromise())))
              _ <- UIO(ctx.flush())
              _ <- loop
            } yield (),
        )

      loop
    }.useNow.flatten
  }
}
