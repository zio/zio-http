package zhttp.experiment

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import zhttp.experiment.HttpMessage.{AnyRequest, CompleteRequest, HResponse}
import zhttp.experiment.Params._
import zhttp.http.{HTTP_CHARSET, Header, Http}
import zhttp.service.UnsafeChannelExecutor
import zio.stream.ZStream
import zio.{Queue, UIO, ZIO}

sealed trait HEndpoint[-R, +E] { self =>
  def cond(f: AnyRequest => Boolean): HEndpoint[R, E]                        = HEndpoint.Condition(f, self)
  def combine[R1 <: R, E1 >: E](other: HEndpoint[R1, E1]): HEndpoint[R1, E1] = HEndpoint.Combine(self, other)
  def +++[R1 <: R, E1 >: E](other: HEndpoint[R1, E1]): HEndpoint[R1, E1]     = self combine other

  private[zhttp] def compile[R1 <: R](zExec: UnsafeChannelExecutor[R1])(implicit
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

        def unsafeEval(program: ZIO[R, Option[Throwable], Any]): Unit = zExec.unsafeExecute_(ctx)(
          program.catchAll({
            case Some(cause) => UIO(ctx.writeAndFlush(serverErrorResponse(cause), void): Unit)
            case None        => UIO(ctx.writeAndFlush(notFoundResponse, void): Unit)
          }),
        )
        msg match {
          case jRequest: HttpRequest =>
            adapter.jRequest = jRequest

            app match {
              case HEndpoint.Fail(cause) =>
                ctx.writeAndFlush(serverErrorResponse(cause), void): Unit

              case HEndpoint.Empty =>
                ctx.writeAndFlush(notFoundResponse, void): Unit

              case HEndpoint.ForAny(http) =>
                unsafeEval {
                  for {
                    res <- http.executeAsZIO(())
                    _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))
                  } yield ()
                }

              case HEndpoint.ForComplete(http) =>
                adapter.completeHttpApp = http
                adapter.isComplete = true
                ctx.read(): Unit

              case HEndpoint.ForAnyRequest(http) =>
                unsafeEval {
                  for {
                    res <- http.executeAsZIO(AnyRequest.from(jRequest))
                    _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))
                  } yield ()
                }

              case HEndpoint.ForBuffered(http) =>
                ctx.channel().config().setAutoRead(false)
                adapter.isBuffered = true

                unsafeEval {
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

              case HEndpoint.Combine(_, _) => ???

              case HEndpoint.Condition(_, _) => ???
            }
          case msg: LastHttpContent  =>
            if (isBuffered) {
              unsafeEval {
                bufferedQueue.offer(msg)
              }
            } else if (adapter.isComplete) {
              adapter.completeBody.writeBytes(msg.content())
              val request = AnyRequest.from(adapter.jRequest)
              unsafeEval {
                for {
                  res <- adapter.completeHttpApp.executeAsZIO(
                    CompleteRequest(request, adapter.completeBody),
                  )
                  _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))

                } yield ()
              }
            }
          case msg: HttpContent      =>
            if (adapter.isBuffered) {
              unsafeEval {
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

      private val notFoundResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, false)

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

  case object Empty extends HEndpoint[Any, Nothing]

  final case class Combine[R, E](self: HEndpoint[R, E], other: HEndpoint[R, E])                extends HEndpoint[R, E]
  final case class Condition[R, E](cond: AnyRequest => Boolean, app: HEndpoint[R, E])          extends HEndpoint[R, E]
  final case class ForComplete[R, E, A, B](http: Http[R, E, CompleteRequest[ByteBuf], HResponse[R, E, ByteBuf]])
      extends HEndpoint[R, E]
  final case class ForBuffered[R, E](http: Http[R, E, BufferedRequest[ByteBuf], HResponse[R, E, ByteBuf]])
      extends HEndpoint[R, E]
  final case class ForAnyRequest[R, E](http: Http[R, E, AnyRequest, HResponse[R, E, ByteBuf]]) extends HEndpoint[R, E]
  final case class ForAny[R, E](http: Http[R, E, Any, HResponse[R, E, ByteBuf]])               extends HEndpoint[R, E]
  final case class Fail[E, A](cause: E)                                                        extends HEndpoint[Any, E]

  def empty: HEndpoint[Any, Nothing] =
    HEndpoint.Empty

  def from[R, E](http: Http[R, E, Any, HResponse[R, E, ByteBuf]])(implicit P: P1): HEndpoint[R, E] = ForAny(http)

  def from[R, E](http: Http[R, E, CompleteRequest[ByteBuf], HResponse[R, E, ByteBuf]])(implicit
    P: P2,
  ): HEndpoint[R, E] =
    ForComplete(http)

  def from[R, E](http: Http[R, E, BufferedRequest[ByteBuf], HResponse[R, E, ByteBuf]])(implicit
    P: P3,
  ): HEndpoint[R, E] =
    ForBuffered(http)

  def from[R, E](http: Http[R, E, AnyRequest, HResponse[R, E, ByteBuf]])(implicit P: P4): HEndpoint[R, E] =
    ForAnyRequest(http)

  def fail[E](cause: E): HEndpoint[Any, E] = HEndpoint.Fail(cause)
}
