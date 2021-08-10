package zhttp.experiment

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import zhttp.experiment.Codec._
import zhttp.experiment.HttpMessage.{AnyRequest, CompleteRequest, HResponse}
import zhttp.experiment.Params._
import zhttp.http.{HTTP_CHARSET, Header, Http}
import zhttp.service.UnsafeChannelExecutor
import zio.stm.TQueue
import zio.stream.ZStream
import zio.{UIO, ZIO}

import scala.annotation.unused

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

      val app                                                                                 = self.asInstanceOf[HEndpoint[R, Throwable]]
      var isComplete: Boolean                                                                 = false
      var isBuffered: Boolean                                                                 = false
      var completeHttpApp: Http[R, Throwable, CompleteRequest[_], HResponse[R, Throwable, _]] = Http.empty
      val completeBody: ByteBuf                                                               = Unpooled.compositeBuffer()
      var bufferedQueue: TQueue[HttpContent]                                                  = _
      var jRequest: HttpRequest                                                               = _
      @unused var encoder: Encoder[_]                                                         = _
      var decoder: Decoder[_]                                                                 = _

      override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {

        val void = ctx.channel().voidPromise()

        def unsafeEval(program: ZIO[R, Option[Throwable], Any]): Unit = zExec.unsafeExecute_(ctx)(
          program.catchAll({
            case Some(cause) => UIO(ctx.writeAndFlush(serverErrorResponse(cause), void): Unit)
            case None        => UIO(ctx.writeAndFlush(notFoundResponse, void): Unit)
          }),
        )
        msg match {
          case msg: HttpRequest     =>
            adapter.jRequest = msg

            app match {
              case HEndpoint.Fail(cause) =>
                ctx.writeAndFlush(serverErrorResponse(cause), void): Unit

              case HEndpoint.Empty =>
                ctx.writeAndFlush(notFoundResponse, void): Unit

              case HEndpoint.Response(_, http) =>
                unsafeEval {
                  for {
                    res <- http.executeAsZIO(())
                    _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))
                  } yield ()
                }

              case HEndpoint.Complete(decoder, encoder, http) =>
                adapter.completeHttpApp = http
                adapter.encoder = encoder
                adapter.decoder = decoder
                adapter.isComplete = true

              case HEndpoint.SomeRequest(_, _) => ???

              case HEndpoint.Buffered(decoder, encoder, http) =>
                ctx.channel().config().setAutoRead(false)
                adapter.encoder = encoder
                adapter.decoder = decoder
                adapter.isBuffered = true

                unsafeEval {
                  for {
                    q   <- TQueue.bounded[HttpContent](1).commit
                    _   <- UIO {
                      adapter.bufferedQueue = q
                      ctx.read()
                    }
                    res <- http.executeAsZIO(
                      AnyRequest
                        .from(adapter.jRequest)
                        .toBufferedRequest(
                          ZStream
                            .fromTQueue(bufferedQueue)
                            .takeWhile(!_.isInstanceOf[LastHttpContent])
                            .map(_.content()),
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
          case msg: LastHttpContent =>
            if (isBuffered) {
              unsafeEval {
                bufferedQueue.offer(msg).commit
              }
            } else if (adapter.isComplete) {
              adapter.completeBody.writeBytes(msg.content())
              val request = AnyRequest.from(adapter.jRequest)
              unsafeEval {
                for {
                  res <- adapter.completeHttpApp.executeAsZIO(
                    CompleteRequest(request, adapter.decoder.decode(adapter.completeBody)),
                  )
                  _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))

                } yield ()
              }
            }
          case msg: HttpContent     =>
            if (adapter.isBuffered) {
              unsafeEval {
                bufferedQueue.offer(msg).commit *> UIO(ctx.read())
              }
            } else if (adapter.isComplete) {
              completeBody.writeBytes(msg.content()): Unit
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

  final case class Combine[R, E](self: HEndpoint[R, E], other: HEndpoint[R, E]) extends HEndpoint[R, E]

  final case class Condition[R, E](cond: AnyRequest => Boolean, app: HEndpoint[R, E]) extends HEndpoint[R, E]

  final case class Complete[R, E, A, B](
    decoder: Decoder[A],
    encoder: Encoder[B],
    http: Http[R, E, CompleteRequest[A], HResponse[R, E, B]],
  ) extends HEndpoint[R, E]

  final case class Buffered[R, E, A, B](
    decoder: Decoder[A],
    encoder: Encoder[B],
    http: Http[R, E, BufferedRequest[A], HResponse[R, E, B]],
  ) extends HEndpoint[R, E]

  final case class SomeRequest[R, E, B](encoder: Encoder[B], http: Http[R, E, AnyRequest, HResponse[R, E, B]])
      extends HEndpoint[R, E]

  final case class Response[R, E, B](encoder: Encoder[B], http: Http[R, E, Any, HResponse[R, E, B]])
      extends HEndpoint[R, E]

  final case class Fail[E, A](cause: E) extends HEndpoint[Any, E]

  def empty: HEndpoint[Any, Nothing] =
    HEndpoint.Empty

  def from[R, E](
    http: Http[R, E, Any, HResponse[R, E, ByteBuf]],
  )(implicit encoder: Encoder[ByteBuf], ev: P1): HEndpoint[R, E] =
    Response(encoder, http)

  def from[R, E](
    http: Http[R, E, CompleteRequest[ByteBuf], HResponse[R, E, ByteBuf]],
  )(implicit decoder: Decoder[ByteBuf], encoder: Encoder[ByteBuf], ev: P2): HEndpoint[R, E] =
    Complete(decoder, encoder, http)

  def from[R, E](
    http: Http[R, E, BufferedRequest[ByteBuf], HResponse[R, E, ByteBuf]],
  )(implicit decoder: Decoder[ByteBuf], encoder: Encoder[ByteBuf], ev: P3): HEndpoint[R, E] =
    Buffered(decoder, encoder, http)

  def from[R, E](
    http: Http[R, E, AnyRequest, HResponse[R, E, ByteBuf]],
  )(implicit encoder: Encoder[ByteBuf], ev: P4): HEndpoint[R, E] =
    SomeRequest(encoder, http)

  def fail[E](cause: E): HEndpoint[Any, E] = HEndpoint.Fail(cause)
}
