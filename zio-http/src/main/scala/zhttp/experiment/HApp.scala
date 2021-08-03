package zhttp.experiment

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import zhttp.experiment.Check.AutoCheck
import zhttp.experiment.HttpMessage.HResponse
import zhttp.http.{Header, HTTP_CHARSET}
import zhttp.service.UnsafeChannelExecutor
import zio.stream.ZStream
import zio.{Chunk, UIO, ZQueue}

import scala.annotation.unused

sealed trait HApp[-R, +E] { self =>
  def combine[R1 <: R, E1 >: E](other: HApp[R1, E1]): HApp[R1, E1] = HApp.Combine(self, other)
  def +++[R1 <: R, E1 >: E](other: HApp[R1, E1]): HApp[R1, E1]     = self combine other

  private[zhttp] def compile[R1 <: R](
    @unused zExec: UnsafeChannelExecutor[R1],
    @unused config: HApp.Config = HApp.Config(),
  )(implicit
    ev: E <:< Throwable,
  ): ChannelHandler =
    new ChannelInboundHandlerAdapter { adapter =>
      override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
        val void = ctx.channel().voidPromise()
        msg match {
          case _: HttpRequest     =>
            self match {
              case HApp.Empty          =>
                ctx.writeAndFlush(HApp.notFoundResponse, void): Unit
              case HApp.Succeed(http)  =>
                zExec.unsafeExecute_(ctx) {
                  (for {
                    res <- http.executeAsZIO(())
                    _   <- UIO(ctx.writeAndFlush(decodeResponse(res), void))
                  } yield ()).catchAll({
                    case Some(cause) => UIO(ctx.writeAndFlush(HApp.serverErrorResponse(cause), void): Unit)
                    case None        => UIO(ctx.writeAndFlush(HApp.notFoundResponse, void): Unit)
                  })
                }
              case HApp.Combine(_, _)  => ???
              case HApp.Complete(_, _) => ???
              case HApp.Partial(_, _)  => ???
            }
          case _: LastHttpContent => ???
          case _: HttpContent     => ???
          case _                  => ???
        }
      }
    }

  private def decodeResponse(res: HResponse[_, _]): HttpResponse = {
    new DefaultHttpResponse(
      HttpVersion.HTTP_1_1,
      res.status.toJHttpStatus,
      Header.disassemble(res.headers),
    )
  }
}

object HApp {

  import HttpVersion._
  import HttpResponseStatus._

  case object Empty                                                                       extends HApp[Any, Nothing]
  case class Combine[R, E](self: HApp[R, E], other: HApp[R, E])                           extends HApp[R, E]
  case class Complete[R, E](check: Check[AnyRequest], http: RHttp[R, E, CompleteRequest]) extends HApp[R, E]
  case class Partial[R, E](check: Check[AnyRequest], http: RHttp[R, E, AnyRequest])       extends HApp[R, E]
  case class Succeed[R, E](http: RHttp[R, E, Any])                                        extends HApp[R, E]

  case class Config(validateHeaders: Boolean = true)

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

  // App Generators
  sealed trait HAppGen[A] {
    def make[R, E](check: Check[AnyRequest], http: RHttp[R, E, A]): HApp[R, E]
  }

  implicit object CompleteRequestHAppGen extends HAppGen[CompleteRequest] {
    override def make[R, E](check: Check[AnyRequest], http: RHttp[R, E, CompleteRequest]): HApp[R, E] =
      HApp.complete(check, http)
  }

  implicit object PartialRequestHAppGen extends HAppGen[AnyRequest] {
    override def make[R, E](check: Check[AnyRequest], http: RHttp[R, E, AnyRequest]): HApp[R, E] =
      HApp.partial(check, http)
  }

  implicit object BufferedRequestHAppGen extends HAppGen[BufferedRequest] {
    override def make[R, E](check: Check[AnyRequest], http: RHttp[R, E, BufferedRequest]): HApp[R, E] =
      HApp.streaming(check, http)
  }

  implicit object AnyHAppGen extends HAppGen[Any] {
    override def make[R, E](check: Check[AnyRequest], http: RHttp[R, E, Any]): HApp[R, E] =
      HApp.streaming(check, http)
  }

  // Constructor
  def apply[R, E, A, C](c: Check[AnyRequest], http: RHttp[R, E, A])(implicit hl: HAppGen[A]): HApp[R, E] =
    hl.make(c, http)

  def apply[R, E, A, C](c: C)(http: RHttp[R, E, A])(implicit ch: AutoCheck[C, AnyRequest], hl: HAppGen[A]): HApp[R, E] =
    HApp(ch.toCheck(c), http)

  def apply[R, E, A](http: RHttp[R, E, A])(implicit hl: HAppGen[A]): HApp[R, E] =
    HApp(Check.isTrue, http)

  def empty: HApp[Any, Nothing] = HApp.Empty

  def succeed[R, E](http: RHttp[R, E, Any]): HApp[R, E] = HApp.Succeed(http)

  // Helper
  private[zhttp] def partial[E, R](check: Check[AnyRequest], http: RHttp[R, E, AnyRequest]): HApp[R, E] =
    Partial(check, http)

  private[zhttp] def streaming[E, R](check: Check[AnyRequest], http: RHttp[R, E, BufferedRequest]): HApp[R, E] =
    HApp.partial(
      check,
      http.contramapM { req: AnyRequest =>
        ZQueue
          .bounded[Chunk[Byte]](1)
          .map(q => req.toBufferedRequest(ZStream.fromChunkQueueWithShutdown(q)))
      },
    )

  private def complete[E, R](check: Check[AnyRequest], http: RHttp[R, E, CompleteRequest]) = {
    HApp.Complete(check, http)
  }

}
