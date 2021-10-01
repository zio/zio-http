package zhttp.http

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.handler.codec.http._
import zhttp.experiment.ContentDecoder
import zhttp.http.HttpApp.InvalidMessage
import zhttp.service.HttpRuntime
import zio._
import zio.stream.ZStream

import java.net.{InetAddress, InetSocketAddress}
import scala.annotation.unused

case class HttpApp[-R, +E](asHttp: Http[R, E, Request, Response[R, E]]) { self =>
  def orElse[R1 <: R, E1 >: E](other: HttpApp[R1, E1]): HttpApp[R1, E1] =
    HttpApp(self.asHttp orElse other.asHttp)

  def defaultWith[R1 <: R, E1 >: E](other: HttpApp[R1, E1]): HttpApp[R1, E1] =
    HttpApp(self.asHttp defaultWith other.asHttp)

  def <>[R1 <: R, E1 >: E](other: HttpApp[R1, E1]): HttpApp[R1, E1] = self orElse other

  def +++[R1 <: R, E1 >: E](other: HttpApp[R1, E1]): HttpApp[R1, E1] = self defaultWith other

  /**
   * Converts a failing Http app into a non-failing one by handling the failure and converting it to a result if
   * possible.
   */
  def silent[R1 <: R, E1 >: E](implicit s: CanBeSilenced[E1, Response[R1, E1]]): HttpApp[R1, E1] =
    self.catchAll(e => Http.succeed(s.silent(e)).toApp)

  /**
   * Combines multiple Http apps into one
   */
  def combine[R1 <: R, E1 >: E](i: Iterable[HttpApp[R1, E1]]): HttpApp[R1, E1] =
    i.reduce(_.defaultWith(_))

  /**
   * Catches all the exceptions that the http app can fail with
   */
  def catchAll[R1 <: R, E1](f: E => HttpApp[R1, E1])(implicit
    @unused ev: CanFail[E],
  ): HttpApp[R1, E1] =
    HttpApp(self.asHttp.catchAll(e => f(e).asHttp).asInstanceOf[Http[R1, E1, Request, Response[R1, E1]]])

  private[zhttp] def compile[R1 <: R](zExec: HttpRuntime[R1])(implicit
    evE: E <:< Throwable,
  ): ChannelHandler =
    new ChannelInboundHandlerAdapter { ad =>
      import HttpResponseStatus._
      import HttpVersion._

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
                (publish, state) <- step.next(Chunk.fromArray(content.array()), nState, isLast)
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
              asHttp.asInstanceOf[Http[R, Throwable, Request, Response[R, Throwable]]],
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

  /**
   * Provides the environment to Http.
   */
  def provide(r: R)(implicit ev: NeedsEnv[R]) = self.asHttp.provide(r)

  /**
   * Provides some of the environment to Http.
   */
  def provideSome[R1 <: R](r: R1 => R)(implicit ev: NeedsEnv[R]) = self.asHttp.provideSome(r)

  /**
   * Provides layer to Http.
   */
  def provideLayer[R0, R1, E1 >: E](layer: ZLayer[R0, E1, R1])(implicit
    ev1: R1 <:< R,
    ev2: NeedsEnv[R],
  ) = self.asHttp.provideLayer(layer)

  /**
   * Provide part of the environment to HTTP that is not part of ZEnv
   */
  def provideCustomLayer[E1 >: E, R1 <: Has[_]](layer: ZLayer[ZEnv, E1, R1])(implicit
    ev: ZEnv with R1 <:< R,
    tagged: Tag[R1],
  ) = self.asHttp.provideCustomLayer(layer)

  /**
   * Provides some of the environment to Http leaving the remainder `R0`.
   */
  def provideSomeLayer[R0 <: Has[_], R1 <: Has[_], E1 >: E](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev: R0 with R1 <:< R, tagged: Tag[R1]) = self.asHttp.provideSomeLayer(layer)
}

object HttpApp {

  final case class InvalidMessage(message: Any) extends IllegalArgumentException {
    override def getMessage: String = s"Endpoint could not handle message: ${message.getClass.getName}"
  }

  /**
   * Creates an Http app from an Http type
   */
  def fromHttp[R, E](http: Http[R, E, Request, Response[R, E]]): HttpApp[R, E] = HttpApp(http)

  /**
   * Creates an Http app which always fails with the same error.
   */
  def fail[E](cause: E): HttpApp[Any, E] = HttpApp(Http.fail(cause))

  /**
   * Creates an Http app which always responds with empty data.
   */
  def empty: HttpApp[Any, Nothing] = HttpApp(Http.empty)

  /**
   * Creates an Http app which accepts a request and produces response.
   */
  def collect[R, E](pf: PartialFunction[Request, Response[R, E]]): HttpApp[R, E] =
    HttpApp(Http.collect(pf))

  /**
   * Creates an Http app which accepts a requests and produces a ZIO as response.
   */
  def collectM[R, E](pf: PartialFunction[Request, ZIO[R, E, Response[R, E]]]): HttpApp[R, E] =
    HttpApp(Http.collectM(pf))

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[R, E](f: Request => ZIO[R, E, Response[R, E]]): HttpApp[R, E] =
    HttpApp(Http.fromEffectFunction(f))

  /**
   * Converts a ZIO to an Http app type
   */
  def responseM[R, E](res: ZIO[R, E, Response[R, E]]): HttpApp[R, E] = HttpApp(Http.fromEffect(res))

  /**
   * Creates an Http app which always responds with the same plain text.
   */
  def text(str: String): HttpApp[Any, Nothing] = HttpApp(Http.succeed(Response.text(str)))

  /**
   * Creates an Http app which always responds with the same value.
   */
  def response[R, E](response: Response[R, E]): HttpApp[R, E] = HttpApp(Http.succeed(response))

  /**
   * Creates an Http app that fails with a NotFound exception.
   */
  def notFound: HttpApp[Any, HttpError] =
    HttpApp(
      Http
        .fromFunction[Request](req => Http.succeed(Response.fromHttpError(HttpError.NotFound(req.url.path))))
        .flatten,
    )

  /**
   * Creates an Http app that responds with 403 - Forbidden status code
   */
  def forbidden(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.Forbidden(msg))))

  /**
   * Creates a Http app from a function from Request to HttpApp
   */
  def fromFunction[R, E, B](f: Request => HttpApp[R, E]): HttpApp[R, E] =
    HttpApp(Http.fromFunction[Request](f(_).asHttp).flatten)

  /**
   * Creates a Http app from a partial function from Request to HttpApp
   */
  def fromPartialFunction[R, E, A, B](f: Request => ZIO[R, Option[E], Response[R, E]]): HttpApp[R, E] =
    HttpApp(Http.fromPartialFunction(f))

}
