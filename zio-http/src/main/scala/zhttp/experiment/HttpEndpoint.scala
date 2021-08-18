package zhttp.experiment

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.handler.codec.http._
import zhttp.experiment.HContent.{Complete, Empty, FromChannel, Streaming}
import zhttp.experiment.HttpMessage.{AnyRequest, CompleteRequest, HResponse}
import zhttp.experiment.ServerEndpoint.CanDecode
import zhttp.http.{HTTP_CHARSET, Header, Http, _}
import zhttp.service.HttpRuntime
import zio.stream.ZStream
import zio.{Queue, UIO, ZIO}

import scala.collection.mutable

sealed trait HttpEndpoint[-R, +E] { self =>
  def orElse[R1 <: R, E1 >: E](other: HttpEndpoint[R1, E1]): HttpEndpoint[R1, E1] = HttpEndpoint.OrElse(self, other)
  def <>[R1 <: R, E1 >: E](other: HttpEndpoint[R1, E1]): HttpEndpoint[R1, E1]     = self orElse other

  private[zhttp] def compile[R1 <: R](zExec: HttpRuntime[R1])(implicit
    evE: E <:< Throwable,
  ): ChannelHandler =
    new ChannelInboundHandlerAdapter { adapter =>
      import HttpVersion._
      import HttpResponseStatus._

      type CompleteHttpApp = Http[R, Throwable, CompleteRequest[ByteBuf], HResponse[R, Throwable, ByteBuf]]
      private var bQueue: Queue[HttpContent] = _
      private var anyRequest: AnyRequest     = _

      private val app: HttpEndpoint[R, Throwable] = self.asInstanceOf[HttpEndpoint[R, Throwable]]
      private var isComplete: Boolean             = false
      private var isBuffered: Boolean             = false
      private var cHttpApp: CompleteHttpApp       = Http.empty
      private val cBody: ByteBuf                  = Unpooled.compositeBuffer()

      override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
        super.channelRegistered(ctx)
        ctx.channel().config().setAutoRead(false)
        ctx.read(): Unit
      }

      override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
        val void = ctx.voidPromise()

        val read = UIO(ctx.read())

        def run[A](
          http: Http[R, Throwable, A, HResponse[R, Throwable, ByteBuf]],
          a: A,
        ): ZIO[R, Option[Throwable], Unit] = for {
          res <- http.executeAsZIO(a)
          _   <- UIO(ctx.write(decodeResponse(res), void))
          _   <- res.content match {
            case Empty             => UIO { ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT) }
            case Complete(data)    => UIO { ctx.writeAndFlush(new DefaultLastHttpContent(data)) }
            case Streaming(stream) =>
              stream.process.map { pull =>
                def loop: ZIO[R, Option[Throwable], Unit] = pull
                  .foldM(
                    {
                      case None        => UIO(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, void)).unit
                      case Some(error) => ZIO.fail(error)
                    },
                    chunks =>
                      ZIO.foreach_(chunks)(bytes => UIO(ctx.writeAndFlush(new DefaultHttpContent(bytes), void))) *>
                        loop,
                  )

                loop
              }.useNow.flatten

            case FromChannel(_) => UIO(ctx.close())
          }
        } yield ()

        def unsafeRun(program: ZIO[R, Option[Throwable], Any]): Unit = zExec.unsafeRun(ctx) {
          program.catchAll {
            case Some(cause) => UIO(ctx.writeAndFlush(serverErrorResponse(cause), void): Unit)
            case None        => UIO(ctx.writeAndFlush(notFoundResponse, void): Unit)
          }
        }

        msg match {
          case jRequest: HttpRequest =>
            val endpoint = getMatchingEndpoint(jRequest)
            if (endpoint == null) ctx.writeAndFlush(notFoundResponse, void): Unit
            else {
              endpoint match {
                case ServerEndpoint.Empty =>
                  ctx.writeAndFlush(notFoundResponse, void): Unit

                case ServerEndpoint.HttpAny(http) =>
                  unsafeRun { run(http, ()) }

                case ServerEndpoint.HttpComplete(http) =>
                  adapter.anyRequest = AnyRequest.from(jRequest)
                  adapter.cHttpApp = http
                  adapter.isComplete = true
                  ctx.read(): Unit

                case ServerEndpoint.HttpAnyRequest(http) =>
                  unsafeRun { run(http, AnyRequest.from(jRequest)) }

                case ServerEndpoint.HttpBuffered(http) =>
                  adapter.isBuffered = true

                  unsafeRun {
                    for {
                      _ <- setupBufferedQueue
                      _ <- read
                      _ <- run(http, makeBufferedRequest(AnyRequest.from(jRequest)))
                    } yield ()
                  }
              }
            }

          case msg: LastHttpContent =>
            if (isBuffered) {
              unsafeRun { bQueue.offer(msg) }
            } else if (adapter.isComplete) {
              adapter.cBody.writeBytes(msg.content())
              unsafeRun { run(adapter.cHttpApp, makeCompleteRequest(anyRequest)) }
            }

          case msg: HttpContent =>
            if (adapter.isBuffered) {
              unsafeRun { bQueue.offer(msg) *> read }
            } else if (adapter.isComplete) {
              cBody.writeBytes(msg.content())
              ctx.read(): Unit
            }

          case _ => () // TODO: Throw an exception
        }
      }

      private def getMatchingEndpoint(request: HttpRequest): ServerEndpoint[R, Throwable] = {
        val stack = mutable.Stack(app)
        while (stack.nonEmpty) {
          stack.pop() match {
            case HttpEndpoint.OrElse(self, other) =>
              stack.push(other)
              stack.push(self)

            case HttpEndpoint.Default(se, check) =>
              if (check.is(request)) return se

            case null => return null
          }
        }
        null
      }

      private def setupBufferedQueue: UIO[Queue[HttpContent]] = for {
        q <- Queue.bounded[HttpContent](1)
        _ <- UIO(adapter.bQueue = q)
      } yield q

      private def makeCompleteRequest(anyRequest: AnyRequest) = {
        CompleteRequest(anyRequest, adapter.cBody)
      }

      private def makeBufferedRequest(anyRequest: AnyRequest): BufferedRequest[ByteBuf] = {
        anyRequest.toBufferedRequest {
          ZStream
            .fromQueue(bQueue)
            .takeUntil(_.isInstanceOf[LastHttpContent])
            .map(_.content())
        }
      }

      private def decodeResponse(res: HResponse[_, _, _]): HttpResponse = {
        new DefaultHttpResponse(HttpVersion.HTTP_1_1, res.status.toJHttpStatus, Header.disassemble(res.headers))
      }

      private val notFoundResponse =
        new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, false)

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

  private[zhttp] final case class Default[R, E](se: ServerEndpoint[R, E], check: Check[HttpRequest] = Check.isTrue)
      extends HttpEndpoint[R, E]

  private[zhttp] final case class OrElse[R, E](self: HttpEndpoint[R, E], other: HttpEndpoint[R, E])
      extends HttpEndpoint[R, E]

  private[zhttp] def mount[R, E](serverEndpoint: ServerEndpoint[R, E]): HttpEndpoint[R, E] =
    Default(serverEndpoint)

  def mount[R, E, A](http: Http[R, E, A, HResponse[R, E, ByteBuf]])(implicit m: CanDecode[A]): HttpEndpoint[R, E] =
    mount(m.endpoint(http))

  def mount[R, E, A](path: Path)(http: Http[R, E, A, HResponse[R, E, ByteBuf]])(implicit
    m: CanDecode[A],
  ): HttpEndpoint[R, E] =
    Default(m.endpoint(http), Check.startsWith(path))

  def fail[E](cause: E): HttpEndpoint[Any, E] = mount(ServerEndpoint.fail(cause))

  def empty: HttpEndpoint[Any, Nothing] = mount(ServerEndpoint.empty)

  def collect[A]: MkCollect[A] = new MkCollect(())

  def collectM[A]: MkCollectM[A] = new MkCollectM(())

  final class MkCollect[A](val unit: Unit) extends AnyVal {
    def apply[R, E](pf: PartialFunction[A, HResponse[R, E, ByteBuf]])(implicit ev: CanDecode[A]): HttpEndpoint[R, E] =
      HttpEndpoint.mount(Http.collect(pf))
  }

  final class MkCollectM[A](val unit: Unit) extends AnyVal {
    def apply[R, E](pf: PartialFunction[A, ZIO[R, E, HResponse[R, E, ByteBuf]]])(implicit
      ev: CanDecode[A],
    ): HttpEndpoint[R, E] =
      HttpEndpoint.mount(Http.collectM(pf))
  }
}
