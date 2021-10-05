package zhttp.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._
import zhttp.experiment.ContentDecoder
import zhttp.http.HttpApp.InvalidMessage
import zhttp.http._
import zio.stream.ZStream
import zio.{Chunk, Promise, UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}

final case class Handler[R, E] private[zhttp] (app: HttpApp[R, E], zExec: HttpRuntime[R])
    extends ChannelInboundHandlerAdapter { ad =>

  private val cBody: ByteBuf                                            = Unpooled.compositeBuffer()
  private var decoder: ContentDecoder[Any, Throwable, Chunk[Byte], Any] = _
  private var completePromise: Promise[Throwable, Any]                  = _
  private var isFirst: Boolean                                          = true
  private var decoderState: Any                                         = _

  override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
    ctx.channel().config().setAutoRead(false)
    ctx.read(): Unit
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    val void = ctx.voidPromise()

    /**
     * Writes ByteBuf data to the Channel
     */
    def unsafeWriteLastContent[A](data: ByteBuf): Unit = {
      ctx.writeAndFlush(new DefaultLastHttpContent(data)): Unit
    }

    /**
     * Writes Binary Stream data to the Channel
     */
    def writeStreamContent[A](stream: ZStream[R, Throwable, ByteBuf]) = {
      stream.process.map { pull =>
        def loop: ZIO[R, Throwable, Unit] = pull
          .foldM(
            {
              case None        => UIO(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, void)).unit
              case Some(error) => ZIO.fail(error)
            },
            chunks =>
              for {
                _ <- ZIO.foreach_(chunks)(buf => UIO(ctx.write(new DefaultHttpContent(buf), void)))
                _ <- UIO(ctx.flush())
                _ <- loop
              } yield (),
          )

        loop
      }.useNow.flatten
    }

    /**
     * Writes any response to the Channel
     */
    def unsafeWriteAnyResponse[A](res: Response[R, Throwable]): Unit = {
      ctx.write(decodeResponse(res), void): Unit
    }

    /**
     * Executes http apps
     */
    def unsafeRun[A](http: Http[R, Throwable, A, Response[R, Throwable]], a: A): Unit = {
      http.execute(a).evaluate match {
        case HExit.Effect(resM) =>
          unsafeRunZIO {
            resM.foldM(
              {
                case Some(cause) => UIO(unsafeWriteAndFlushErrorResponse(cause))
                case None        => UIO(unsafeWriteAndFlushNotFoundResponse())
              },
              res =>
                for {
                  _ <- UIO(unsafeWriteAnyResponse(res))
                  _ <- res.data match {
                    case HttpData.Empty =>
                      UIO(unsafeWriteAndFlushNotFoundResponse())

                    case HttpData.Text(data, charset) =>
                      UIO(unsafeWriteLastContent(Unpooled.copiedBuffer(data, charset)))

                    case HttpData.BinaryN(data) => UIO(unsafeWriteLastContent(data))

                    case HttpData.Binary(data) =>
                      UIO(unsafeWriteLastContent(Unpooled.copiedBuffer(data.toArray)))

                    case HttpData.BinaryStream(stream) =>
                      writeStreamContent(stream.mapChunks(a => Chunk(Unpooled.copiedBuffer(a.toArray))))

                    case HttpData.Socket(_) => ???
                  }
                } yield (),
            )
          }

        case HExit.Success(a) =>
          unsafeWriteAnyResponse(a)
          a.data match {
            case HttpData.Empty =>
              unsafeWriteAndFlushNotFoundResponse()

            case HttpData.Text(data, charset) =>
              unsafeWriteLastContent(Unpooled.copiedBuffer(data, charset))

            case HttpData.BinaryN(data) =>
              unsafeWriteLastContent(data)

            case HttpData.Binary(data) =>
              unsafeWriteLastContent(Unpooled.copiedBuffer(data.toArray))

            case HttpData.BinaryStream(stream) =>
              unsafeRunZIO(writeStreamContent(stream.mapChunks(a => Chunk(Unpooled.copiedBuffer(a.toArray)))))

            case HttpData.Socket(_) => ???
          }

        case HExit.Failure(e) => unsafeWriteAndFlushErrorResponse(e)
        case HExit.Empty      => unsafeWriteAndFlushNotFoundResponse()
      }
    }

    /**
     * Writes error response to the Channel
     */
    def unsafeWriteAndFlushErrorResponse(cause: Throwable): Unit = {
      ctx.writeAndFlush(serverErrorResponse(cause), void): Unit
    }

    /**
     * Writes not found error response to the Channel
     */
    def unsafeWriteAndFlushNotFoundResponse(): Unit = {
      ctx.writeAndFlush(notFoundResponse, void): Unit
    }

    /**
     * Executes program
     */
    def unsafeRunZIO(program: ZIO[R, Throwable, Any]): Unit = zExec.unsafeRun(ctx) {
      program
    }

    /**
     * Decodes content and executes according to the ContentDecoder provided
     */
    def decodeContent(
      content: ByteBuf,
      decoder: ContentDecoder[Any, Throwable, Chunk[Byte], Any],
      isLast: Boolean,
    ): Unit = {
      decoder match {
        case ContentDecoder.Text =>
          cBody.writeBytes(content)
          if (isLast) {
            unsafeRunZIO(ad.completePromise.succeed(cBody.toString(HTTP_CHARSET)))
          } else {
            ctx.read(): Unit
          }

        case step: ContentDecoder.Step[_, _, _, _, _] =>
          if (ad.isFirst) {
            ad.decoderState = step.state
            ad.isFirst = false
          }
          val nState = ad.decoderState

          unsafeRunZIO(for {
            (publish, state) <- step
              .asInstanceOf[ContentDecoder.Step[R, Throwable, Any, Chunk[Byte], Any]]
              .next(Chunk.fromArray(content.array()), nState, isLast)
            _                <- publish match {
              case Some(out) => ad.completePromise.succeed(out)
              case None      => ZIO.unit
            }
            _                <- UIO {
              ad.decoderState = state
              if (!isLast) ctx.read(): Unit
            }
          } yield ())

      }
    }

    msg match {
      case jRequest: HttpRequest =>
        // TODO: Unnecessary requirement
        // `autoRead` is set when the channel is registered in the event loop.
        // The explicit call here is added to make unit tests work properly
        ctx.channel().config().setAutoRead(false)

        unsafeRun(
          app.asHttp.asInstanceOf[Http[R, Throwable, Request, Response[R, Throwable]]],
          new Request {
            override def decodeContent[R0, B](
              decoder: ContentDecoder[R0, Throwable, Chunk[Byte], B],
            ): ZIO[R0, Throwable, B] =
              ZIO.effectSuspendTotal {
                if (ad.decoder != null)
                  ZIO.fail(ContentDecoder.Error.ContentDecodedOnce)
                else
                  for {
                    p <- Promise.make[Throwable, B]
                    _ <- UIO {
                      ad.decoder = decoder.asInstanceOf[ContentDecoder[Any, Throwable, Chunk[Byte], B]]
                      ad.completePromise = p.asInstanceOf[Promise[Throwable, Any]]
                      ctx.read(): Unit
                    }
                    b <- p.await
                  } yield b
              }

            override def method: Method        = Method.fromHttpMethod(jRequest.method())
            override def url: URL              = URL.fromString(jRequest.uri()).getOrElse(null)
            override def headers: List[Header] = Header.make(jRequest.headers())
            override def remoteAddress: Option[InetAddress] = {
              ctx.channel().remoteAddress() match {
                case m: InetSocketAddress => Some(m.getAddress())
                case _                    => None
              }
            }
          },
        )

      case msg: LastHttpContent =>
        if (decoder != null) {
          decodeContent(msg.content(), decoder, true)
        }

      case msg: HttpContent =>
        if (decoder != null) {
          decodeContent(msg.content(), decoder, false)
        }

      case msg => ctx.fireExceptionCaught(InvalidMessage(msg)): Unit
    }
  }

  private def decodeResponse(res: Response[_, _]): HttpResponse = {
    new DefaultHttpResponse(HttpVersion.HTTP_1_1, res.status.asJava, Header.disassemble(res.headers))
  }

  private val notFoundResponse =
    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, false)

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

}
