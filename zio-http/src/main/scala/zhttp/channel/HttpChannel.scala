package zhttp.channel

import io.netty.channel.{ChannelInboundHandlerAdapter => JChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpContent => JHttpContent, HttpObject => JHttpObject, HttpRequest => JHttpRequest}
import zhttp.core.{JChannelHandler, JChannelHandlerContext}
import zhttp.http.{Http, HttpResult}
import zhttp.service.UnsafeChannelExecutor
import zio.ZIO

/**
 * Low level handle to control the flow of messages over a network channel.
 *
 * TODO: Benchmark and optimize for performance
 */

case class HttpChannel[-R, +E, -A, +B](asHttp: Http[R, E, Event[A], Operation[B]]) { self =>
  def ++[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: HttpChannel[R1, E1, A1, B1]): HttpChannel[R1, E1, A1, B1] =
    self combine other

  def <>[R1 <: R, E1, A1 <: A, B1 >: B](other: HttpChannel[R1, E1, A1, B1]): HttpChannel[R1, E1, A1, B1] =
    self orElse other

  def combine[R1 <: R, E1 >: E, A1 <: A, B1 >: B](other: HttpChannel[R1, E1, A1, B1]): HttpChannel[R1, E1, A1, B1] =
    HttpChannel(self.asHttp.flatMap(a => other.asHttp.map(b => a ++ b)))

  def map[C](bc: B => C): HttpChannel[R, E, A, C] =
    HttpChannel(self.asHttp.map(_.map(bc)))

  def mapError[E1 >: E, E2](ee: E1 => E2): HttpChannel[R, E2, A, B] =
    HttpChannel(self.asHttp.mapError(ee))

  def contramap[X](xa: X => A): HttpChannel[R, E, X, B] =
    HttpChannel(self.asHttp.contramap[Event[X]](ev => ev.map(xa)))

  def orElse[R1 <: R, E1, A1 <: A, B1 >: B](other: HttpChannel[R1, E1, A1, B1]): HttpChannel[R1, E1, A1, B1] =
    HttpChannel(self.asHttp.orElse(other.asHttp))
}

object HttpChannel {
  def succeed[B](op: Operation[B]): HttpChannel[Any, Nothing, Any, B] =
    HttpChannel(Http.succeed(op))

  def fail[E](error: E): HttpChannel[Any, E, Any, Nothing] =
    HttpChannel(Http.fail(error))

  def write[B](b: B): HttpChannel[Any, Nothing, Any, B] =
    HttpChannel.succeed(Operation.write(b))

  def flush: HttpChannel[Any, Nothing, Any, Nothing] =
    HttpChannel.succeed(Operation.flush)

  def read: HttpChannel[Any, Nothing, Any, Nothing] =
    HttpChannel.succeed(Operation.read)

  def empty: HttpChannel[Any, Nothing, Any, Nothing] =
    HttpChannel.succeed(Operation.empty)

  def echoBody[A]: HttpChannel[Any, Nothing, A, A] =
    HttpChannel.concatMap[A] {
      case Event.Read(data) => HttpChannel.write(data)
      case Event.Complete   => HttpChannel.flush ++ HttpChannel.read
    }

  def fromEffect[R, E, A](eff: ZIO[R, E, Operation[A]]): HttpChannel[R, E, Any, A] =
    HttpChannel(Http.fromEffect(eff))

  def collect[A]: MkCollect[A] =
    new MkCollect(())

  def concatMap[A]: MkConcatMap[A] =
    new MkConcatMap[A](())

  final class MkCollect[A](val unit: Unit) extends AnyVal {
    def apply[B](pf: PartialFunction[Event[A], Operation[B]]): HttpChannel[Any, Nothing, A, B] =
      HttpChannel(Http.collect(pf))
  }

  final class MkConcatMap[A](val unit: Unit) extends AnyVal {
    def apply[R, E, B](pf: PartialFunction[Event[A], HttpChannel[R, E, Any, B]]): HttpChannel[R, E, A, B] =
      HttpChannel(Http.collect(pf).flatMap(_.asHttp))
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
          case HttpResult.Success(a)      => a.execute(ctx)
          case HttpResult.Failure(e)      => ctx.fireExceptionCaught(e): Unit
          case HttpResult.Effect(program) => zExec.unsafeExecute(ctx, program) { _ => ??? }
          case HttpResult.Empty           => ???
        }
      }

      private def executeFirst(ctx: JChannelHandlerContext, event: JHttpRequest): Unit =
        channel.execute(event).evaluate match {
          case HttpResult.Empty           => ()
          case HttpResult.Success(a)      => dataChannel = a
          case HttpResult.Failure(e)      => ctx.fireExceptionCaught(e): Unit
          case HttpResult.Effect(program) => zExec.unsafeExecute(ctx, program) { _ => ??? }
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
