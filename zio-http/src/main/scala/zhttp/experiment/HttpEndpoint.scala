package zhttp.experiment

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.handler.codec.http._
import zhttp.experiment.HttpEndpoint.InvalidMessage
import zhttp.http._
import zhttp.service.HttpRuntime
import zio.stream.ZStream
import zio.{Chunk, Promise, UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}

case class HttpEndpoint[-R, +E](http: Http[R, E, Request, Response[R, E]]) { self =>
  def orElse[R1 <: R, E1 >: E](other: HttpEndpoint[R1, E1]): HttpEndpoint[R1, E1] =
    HttpEndpoint(self.http orElse other.http)

  def defaultWith[R1 <: R, E1 >: E](other: HttpEndpoint[R1, E1]): HttpEndpoint[R1, E1] =
    HttpEndpoint(self.http defaultWith other.http)

  def <>[R1 <: R, E1 >: E](other: HttpEndpoint[R1, E1]): HttpEndpoint[R1, E1] = self orElse other

  def +++[R1 <: R, E1 >: E](other: HttpEndpoint[R1, E1]): HttpEndpoint[R1, E1] = self defaultWith other

  private[zhttp] def compile[R1 <: R](zExec: HttpRuntime[R1])(implicit
    evE: E <:< Throwable,
  ): ChannelHandler =
    new ChannelInboundHandlerAdapter { ad =>
      import HttpResponseStatus._
      import HttpVersion._

      private val cBody: ByteBuf                               = Unpooled.compositeBuffer()
      private var decoder: ContentDecoder[Any, Throwable, Any] = _
      private var completePromise: Promise[Throwable, Any]     = _
      private var isFirst: Boolean                             = true
      private var decoderState: Any                            = _

      override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
        ctx.channel().config().setAutoRead(false)
        ctx.read(): Unit
      }

      override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
        val void = ctx.voidPromise()

        def unsafeWriteLastContent[A](data: ByteBuf): Unit = {
          ctx.writeAndFlush(new DefaultLastHttpContent(data)): Unit
        }

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

        def unsafeWriteAnyResponse[A](res: Response[R, Throwable]): Unit = {
          ctx.write(decodeResponse(res), void): Unit
        }

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

        def unsafeWriteAndFlushErrorResponse(cause: Throwable): Unit = {
          ctx.writeAndFlush(serverErrorResponse(cause), void): Unit
        }

        def unsafeWriteAndFlushNotFoundResponse(): Unit = {
          ctx.writeAndFlush(notFoundResponse, void): Unit
        }

        def unsafeRunZIO(program: ZIO[R, Throwable, Any]): Unit = zExec.unsafeRun(ctx) {
          program
        }

        def decodeContent(
          content: ByteBuf,
          decoder: ContentDecoder[Any, Throwable, Any],
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

            case ContentDecoder.Custom(state, run) =>
              if (ad.isFirst) {
                ad.decoderState = state
                ad.isFirst = false
              }
              val nState = ad.decoderState

              unsafeRunZIO(for {
                (publish, state) <- run(Chunk.fromArray(content.array()), nState, isLast)
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
              http.asInstanceOf[Http[R, Throwable, Request, Response[R, Throwable]]],
              new Request {
                override def decodeContent[R0, B](
                  decoder: ContentDecoder[R0, Throwable, B],
                ): ZIO[R0, Throwable, B] =
                  ZIO.effectSuspendTotal {
                    if (ad.decoder != null)
                      ZIO.fail(ContentDecoder.Error.ContentDecodedOnce)
                    else
                      for {
                        p <- Promise.make[Throwable, B]
                        _ <- UIO {
                          ad.decoder = decoder.asInstanceOf[ContentDecoder[Any, Throwable, B]]
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
}

object HttpEndpoint {

  final case class InvalidMessage(message: Any) extends IllegalArgumentException {
    override def getMessage: String = s"Endpoint could not handle message: ${message.getClass.getName}"
  }
  def mount[R, E](http: Http[R, E, Request, Response[R, E]]): HttpEndpoint[R, E] = HttpEndpoint(http)

  def fail[E](cause: E): HttpEndpoint[Any, E] = HttpEndpoint(Http.fail(cause))

  def empty: HttpEndpoint[Any, Nothing] = HttpEndpoint(Http.empty)

  def collect[R, E](pf: PartialFunction[Request, Response[R, E]]): HttpEndpoint[R, E] =
    HttpEndpoint(Http.collect(pf))

  def collectM[R, E](pf: PartialFunction[Request, ZIO[R, E, Response[R, E]]]): HttpEndpoint[R, E] =
    HttpEndpoint(Http.collectM(pf))

  /**
   * Creates an Http endpoint from a function that returns a ZIO
   */
  def fromEffectFunction[R, E](f: Request => ZIO[R, E, Response[R, E]]): HttpEndpoint[R, E] =
    HttpEndpoint(Http.fromEffectFunction(f))

  /**
   * Converts a ZIO to an Http Endpoint type
   */
  def responseM[R, E](res: ZIO[R, E, Response[R, E]]): HttpEndpoint[R, E] = HttpEndpoint(Http.fromEffect(res))

  /**
   * Creates an HTTP Endpoint which always responds with the same plain text.
   */
  def text(str: String): HttpEndpoint[Any, Nothing] = HttpEndpoint(Http.succeed(Response.text(str)))

  /**
   * Creates an HTTP Endpoint which always responds with the same value.
   */
  def response[R, E](response: Response[R, E]): HttpEndpoint[R, E] = HttpEndpoint(Http.succeed(response))

  /**
   * Creates an HTTP Endpoint that fails with a NotFound exception.
   */
  def notFound: HttpEndpoint[Any, HttpError] =
    HttpEndpoint(
      Http
        .fromFunction[Request](req => Http.succeed(Response.fromHttpError(HttpError.NotFound(req.url.path))))
        .flatten,
    )

  /**
   * Creates an HTTP Endpoint that responds with 403 - Forbidden status code
   */
  def forbidden(msg: String): HttpEndpoint[Any, Nothing] =
    HttpEndpoint(Http.succeed(Response.fromHttpError(HttpError.Forbidden(msg))))

  /**
   * Creates a Http Endpoint from a function from Request to HttpEndpoint
   */
  def fromFunction[R, E, B](f: Request => HttpEndpoint[R, E]): HttpEndpoint[R, E] =
    HttpEndpoint(Http.fromFunction[Request](f(_).http).flatten)
}
