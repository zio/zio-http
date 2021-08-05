package zhttp.experiment

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import zhttp.experiment.HttpMessage.{AnyRequest, CompleteRequest, HResponse}
import zhttp.http.{HTTP_CHARSET, Header, Http}
import zhttp.service.UnsafeChannelExecutor
import zio.{UIO, ZIO}
import scala.annotation.unused
import Params._
import Codec._

sealed trait HApp[-R, +E] { self =>
  def combine[R1 <: R, E1 >: E](other: HApp[R1, E1]): HApp[R1, E1] = HApp.Combine(self, other)
  def +++[R1 <: R, E1 >: E](other: HApp[R1, E1]): HApp[R1, E1]     = self combine other

  private[zhttp] def compile[R1 <: R](zExec: UnsafeChannelExecutor[R1])(implicit
    evE: E <:< Throwable,
  ): ChannelHandler =
    new ChannelInboundHandlerAdapter { adapter =>
      import HttpVersion._
      import HttpResponseStatus._

      val app                                                                                 = self.asInstanceOf[HApp[R, Throwable]]
      var completeHttpApp: Http[R, Throwable, CompleteRequest[_], HResponse[R, Throwable, _]] = Http.empty
      val completeBody: ByteBuf                                                               = Unpooled.compositeBuffer()
      var jRequest: HttpRequest                                                               = _
      @unused var encoder: Encoder[_]                                                         = _
      var decoder: Decoder[_]                                                                 = _

      override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
        def eval(program: ZIO[R, Throwable, Any]): Unit = zExec.unsafeExecute_(ctx)(program)

        val void = ctx.channel().voidPromise()
        msg match {
          case msg: HttpRequest     =>
            adapter.jRequest = msg

            app match {
              case HApp.Fail(cause) =>
                ctx.writeAndFlush(serverErrorResponse(cause), void): Unit

              case HApp.Empty =>
                ctx.writeAndFlush(notFoundResponse, void): Unit

              case HApp.Response(_, http) =>
                eval {
                  (for {
                    res <- http.executeAsZIO(())
                    _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))
                  } yield ()).catchAll({
                    case Some(cause) => UIO(ctx.writeAndFlush(serverErrorResponse(cause), void): Unit)
                    case None        => UIO(ctx.writeAndFlush(notFoundResponse, void): Unit)
                  })
                }

              case HApp.Complete(decoder, encoder, http) =>
                adapter.completeHttpApp = http
                adapter.encoder = encoder
                adapter.decoder = decoder

              case HApp.Unknown(_, _) => ???

              case HApp.Buffered(_, _, _) => ???

              case HApp.Combine(_, _) => ???
            }
          case msg: LastHttpContent =>
            adapter.completeBody.writeBytes(msg.content())
            val request = AnyRequest.from(adapter.jRequest)
            eval {
              (for {
                res <- adapter.completeHttpApp.executeAsZIO(
                  CompleteRequest(request, adapter.decoder.decode(adapter.completeBody)),
                )
                _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))

              } yield ()).catchAll({
                case Some(_) => ???
                case None    => ???
              })
            }
          case msg: HttpContent     => completeBody.writeBytes(msg.content()): Unit
          case _                    => ???
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

object HApp {

  case object Empty extends HApp[Any, Nothing]

  final case class Combine[R, E](self: HApp[R, E], other: HApp[R, E]) extends HApp[R, E]

  final case class Complete[R, E, A, B](
    decoder: Decoder[A],
    encoder: Encoder[B],
    http: Http[R, E, CompleteRequest[A], HResponse[R, E, B]],
  ) extends HApp[R, E]

  final case class Buffered[R, E, A, B](
    decoder: Decoder[A],
    encoder: Encoder[B],
    http: Http[R, E, BufferedRequest[A], HResponse[R, E, B]],
  ) extends HApp[R, E]

  final case class Unknown[R, E, B](encoder: Encoder[B], http: Http[R, E, AnyRequest, HResponse[R, E, B]])
      extends HApp[R, E]

  final case class Response[R, E, B](encoder: Encoder[B], http: Http[R, E, Any, HResponse[R, E, B]]) extends HApp[R, E]

  final case class Fail[E, A](cause: E) extends HApp[Any, E]

  def empty: HApp[Any, Nothing] =
    HApp.Empty

  def from[R, E](
    http: Http[R, E, Any, HResponse[R, E, ByteBuf]],
  )(implicit encoder: Encoder[ByteBuf], ev: P1): HApp[R, E] =
    Response(encoder, http)

  def from[R, E](
    http: Http[R, E, CompleteRequest[ByteBuf], HResponse[R, E, ByteBuf]],
  )(implicit decoder: Decoder[ByteBuf], encoder: Encoder[ByteBuf], ev: P2): HApp[R, E] =
    Complete(decoder, encoder, http)

  def from[R, E](
    http: Http[R, E, BufferedRequest[ByteBuf], HResponse[R, E, ByteBuf]],
  )(implicit decoder: Decoder[ByteBuf], encoder: Encoder[ByteBuf], ev: P3): HApp[R, E] =
    Buffered(decoder, encoder, http)

  def from[R, E](
    http: Http[R, E, AnyRequest, HResponse[R, E, ByteBuf]],
  )(implicit encoder: Encoder[ByteBuf], ev: P4): HApp[R, E] =
    Unknown(encoder, http)

  def fail[E](cause: E): HApp[Any, E] = HApp.Fail(cause)
}
