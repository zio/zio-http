package zhttp.experiment

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import zhttp.experiment.HttpMessage.{AnyRequest, CompleteRequest, HResponse}
import zhttp.experiment.ServerEndpoint.IsEndpoint
import zhttp.http.{Header, Http, HTTP_CHARSET}
import zhttp.service.HttpRuntime
import zio.stream.ZStream
import zio.{Queue, UIO, ZIO}

sealed trait HEndpoint[-R, +E] { self =>
  def combine[R1 <: R, E1 >: E](other: HEndpoint[R1, E1]): HEndpoint[R1, E1] = HEndpoint.OrElse(self, other)
  def +++[R1 <: R, E1 >: E](other: HEndpoint[R1, E1]): HEndpoint[R1, E1]     = self combine other
  def check: Check[AnyRequest]

  private[zhttp] def compile[R1 <: R](zExec: HttpRuntime[R1])(implicit
    evE: E <:< Throwable,
  ): ChannelHandler =
    new ChannelInboundHandlerAdapter { adapter =>
      import HttpVersion._
      import HttpResponseStatus._

      val app                                                                                             = self.asInstanceOf[HEndpoint[R, Throwable]]
      var isComplete: Boolean                                                                             = false
      var isBuffered: Boolean                                                                             = false
      var completeHttpApp: Http[R, Throwable, CompleteRequest[ByteBuf], HResponse[R, Throwable, ByteBuf]] = Http.empty
      val completeBody: ByteBuf                                                                           = Unpooled.compositeBuffer()
      var bufferedQueue: Queue[HttpContent]                                                               = _
      var jRequest: HttpRequest                                                                           = _

      override def channelRegistered(ctx: ChannelHandlerContext): Unit = {
        super.channelRegistered(ctx)
        ctx.channel().config().setAutoRead(false)
        ctx.read(): Unit
      }

      override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {

        val void = ctx.channel().voidPromise()

        def unsafeRun(program: ZIO[R, Option[Throwable], Any]): Unit = zExec.unsafeRun(ctx)(
          program.catchAll {
            case Some(cause) => UIO(ctx.writeAndFlush(serverErrorResponse(cause), void): Unit)
            case None        => UIO(ctx.writeAndFlush(notFoundResponse, void): Unit)
          },
        )
        msg match {
          case jRequest: HttpRequest =>
            adapter.jRequest = jRequest

            def execute(endpoint: HEndpoint[R, Throwable]): Boolean = {
              endpoint match {
                case HEndpoint.OrElse(a, b) =>
                  if (execute(a)) {
                    true
                  } else {
                    execute(b)
                  }

                case HEndpoint.Default(endpoint, check) =>
                  if (check.is(AnyRequest.from(jRequest))) {
                    endpoint match {
                      case ServerEndpoint.Fail(cause) =>
                        ctx.writeAndFlush(serverErrorResponse(cause), void): Unit

                      case ServerEndpoint.Empty =>
                        ctx.writeAndFlush(notFoundResponse, void): Unit

                      case ServerEndpoint.HttpAny(http) =>
                        unsafeRun {
                          for {
                            res <- http.executeAsZIO(())
                            _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))
                          } yield ()
                        }

                      case ServerEndpoint.HttpComplete(http) =>
                        adapter.completeHttpApp = http
                        adapter.isComplete = true
                        ctx.read(): Unit

                      case ServerEndpoint.HttpAnyRequest(http) =>
                        unsafeRun {
                          for {
                            res <- http.executeAsZIO(AnyRequest.from(jRequest))
                            _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))
                          } yield ()
                        }

                      case ServerEndpoint.HttpBuffered(http) =>
                        ctx.channel().config().setAutoRead(false)
                        adapter.isBuffered = true

                        unsafeRun {
                          for {
                            q   <- Queue.bounded[HttpContent](1)
                            _   <- UIO {
                              adapter.bufferedQueue = q
                              ctx.read()
                            }
                            res <- http.executeAsZIO(
                              AnyRequest
                                .from(adapter.jRequest)
                                .toBufferedRequest(
                                  ZStream
                                    .fromQueue(bufferedQueue)
                                    .takeWhile(!_.isInstanceOf[LastHttpContent])
                                    .map(buf => buf.content()),
                                ),
                            )

                            _ <- UIO {
                              ctx.writeAndFlush(decodeResponse(res), void)
                            }

                          } yield ()
                        }

                    }

                    true
                  } else {
                    false
                  }
              }
            }

            if (!execute(app)) {
              ctx.writeAndFlush(notFoundResponse, void): Unit
            }

          case msg: LastHttpContent =>
            if (isBuffered) {
              unsafeRun {
                bufferedQueue.offer(msg)
              }
            } else if (adapter.isComplete) {
              adapter.completeBody.writeBytes(msg.content())
              val request = AnyRequest.from(adapter.jRequest)
              unsafeRun {
                for {
                  res <- adapter.completeHttpApp.executeAsZIO(
                    CompleteRequest(request, adapter.completeBody),
                  )
                  _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))

                } yield ()
              }
            }
          case msg: HttpContent     =>
            if (adapter.isBuffered) {
              unsafeRun {
                bufferedQueue.offer(msg) *> UIO(ctx.read())
              }
            } else if (adapter.isComplete) {
              completeBody.writeBytes(msg.content())
              ctx.read(): Unit
            }

          case _ => ???
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

object HEndpoint {

  final case class Default[R, E](se: ServerEndpoint[R, E], check: Check[AnyRequest] = Check.isTrue)
      extends HEndpoint[R, E]

  final case class OrElse[R, E](self: HEndpoint[R, E], other: HEndpoint[R, E]) extends HEndpoint[R, E] {
    override def check: Check[AnyRequest] = self.check || other.check
  }

  def from[R, E](serverEndpoint: ServerEndpoint[R, E]): HEndpoint[R, E] =
    Default(serverEndpoint)

  def from[R, E, A](http: Http[R, E, A, HResponse[R, E, ByteBuf]])(implicit m: IsEndpoint[A]): HEndpoint[R, E] =
    from(m.endpoint(http))

  def from[R, E, A](path: String)(http: Http[R, E, A, HResponse[R, E, ByteBuf]])(implicit
    m: IsEndpoint[A],
  ): HEndpoint[R, E] =
    Default(m.endpoint(http), Check.startsWith(path))

  def fail[E](cause: E): HEndpoint[Any, E] = from(ServerEndpoint.fail(cause))

  def empty: HEndpoint[Any, Nothing] = from(ServerEndpoint.empty)
}
