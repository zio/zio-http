package zhttp.channel

import io.netty.channel.{ChannelInboundHandlerAdapter => JChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpContent => JHttpContent, HttpObject => JHttpObject, HttpRequest => JHttpRequest}
import zhttp.core.{JChannelHandler, JChannelHandlerContext}
import zhttp.http.{Http, HttpResult}
import zhttp.service.UnsafeChannelExecutor

case class HttpChannel[-R, +E, -A, +B](asHttp: Http[R, E, Event[A], Operation[R, E, B]]) { self =>
  def ++[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: HttpChannel[R1, E1, A1, B1]): HttpChannel[R1, E1, A1, B1] =
    HttpChannel(self.asHttp.flatMap(b => other.asHttp.map(b1 => b ++ b1)))
}

object HttpChannel {
  import Event._

  def succeed[R, E, B](op: Operation[R, E, B]): HttpChannel[R, E, Any, B] =
    HttpChannel(Http.succeed(op))
  def write[B](b: B): HttpChannel[Any, Nothing, Any, B]                   =
    HttpChannel.succeed(Operation.write(b))
  def flush: HttpChannel[Any, Nothing, Any, Nothing]                      =
    HttpChannel.succeed(Operation.flush)
  def read: HttpChannel[Any, Nothing, Any, Nothing]                       =
    HttpChannel.succeed(Operation.read)
  def empty: HttpChannel[Any, Nothing, Any, Nothing]                      =
    HttpChannel.succeed(Operation.empty)
  def echoBody[A]: HttpChannel[Any, Nothing, A, A]                        =
    HttpChannel.collect[A] {
      case Read(data) => Operation.write(data)
      case Complete   => Operation.flush ++ Operation.read
    }
  def collect[A]: MkHttpChannel[A]                                        =
    new MkHttpChannel(())

  final class MkHttpChannel[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[Event[A], Operation[R, E, B]]): HttpChannel[R, E, A, B] =
      HttpChannel(Http.collect(pf))
  }

  /**
   * Compiles a HttpChannel into a netty channel handler
   */
  private[zhttp] def compile[R, E <: Throwable](
    zExec: UnsafeChannelExecutor[R],
    channel: Http[R, E, JHttpRequest, HttpChannel[R, E, JHttpContent, JHttpObject]],
  ): JChannelHandler =
    new JChannelInboundHandlerAdapter {
      private var dataChannel: HttpChannel[R, E, JHttpContent, JHttpObject] = HttpChannel.empty

      private def execute(ctx: JChannelHandlerContext, event: Event[JHttpContent]): Unit = {
        dataChannel.asHttp.execute(event).evaluate match {
          case HttpResult.Empty           => ()
          case HttpResult.Success(a)      => a.execute(ctx)
          case HttpResult.Failure(e)      => ctx.fireExceptionCaught(e): Unit
          case HttpResult.Effect(program) =>
            zExec.unsafeExecute(ctx, program) { _ =>
              ???
            }
        }
      }

      private def executeFirst(ctx: JChannelHandlerContext, event: JHttpRequest): Unit = {
        channel.execute(event).evaluate match {
          case HttpResult.Empty           => ()
          case HttpResult.Success(a)      => dataChannel = a
          case HttpResult.Failure(e)      => ctx.fireExceptionCaught(e): Unit
          case HttpResult.Effect(program) =>
            zExec.unsafeExecute(ctx, program) { _ =>
              ???
            }
        }
      }

      override def channelReadComplete(ctx: JChannelHandlerContext): Unit =
        execute(ctx, Event.Complete)

      override def channelRead(ctx: JChannelHandlerContext, msg: Any): Unit =
        msg match {
          case msg: JHttpRequest => executeFirst(ctx, msg)
          case msg: JHttpContent => execute(ctx, Event.Read(msg))
          case _                 => ctx.fireExceptionCaught(new Error("Unhandled message on HttpChannel")): Unit
        }

      override def channelRegistered(ctx: JChannelHandlerContext): Unit = {
        ctx.channel.config.setAutoRead(false)
        ctx.channel.read(): Unit
      }
    }
}
