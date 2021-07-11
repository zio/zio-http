package zhttp.channel

import io.netty.channel.{ChannelInboundHandlerAdapter => JChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpContent => JHttpContent, HttpObject => JHttpObject, HttpRequest => JHttpRequest}
import zhttp.core.{JChannelHandler, JChannelHandlerContext}
import zhttp.http.{Http, HttpResult}
import zhttp.service.UnsafeChannelExecutor

/**
 * Low level handle to control the flow of messages over a network channel.
 *
 * TODO: Benchmark and optimize for performance
 */

sealed trait HttpChannel[-R, +E, -A, +B] { self =>
  type State
  def initialize: State
  def execute(state: State, event: Event[A]): HttpResult[R, E, Operation[B, State]]
  def executeWithAnyState(state: Any, event: Event[A]): HttpResult[R, E, Operation[B, State]] =
    execute(state.asInstanceOf[State], event)
}

object HttpChannel {
  type Aux[-R, +E, S, -A, +B] = HttpChannel[R, E, A, B] {
    type State = S
  }

  def apply[R, E, A, B, S](
    state: S,
  )(exe: (S, Event[A]) => HttpResult[R, E, Operation[B, S]]): HttpChannel.Aux[R, E, S, A, B] =
    new HttpChannel[R, E, A, B] {
      override type State = S
      override def execute(state: State, event: Event[A]): HttpResult[R, E, Operation[B, State]] = exe(state, event)
      override def initialize: State                                                             = state
    }

  def collectM[R, E, A, B](exe: Event[A] => HttpResult[R, E, Operation[B, Unit]]): HttpChannel.Aux[R, E, Unit, A, B] =
    HttpChannel(())((_, event) => exe(event))

  def collect[A]: MkCollect[A]         = new MkCollect[A](())
  def collectWith[A]: MkCollectWith[A] = new MkCollectWith[A](())

  final class MkCollect[A](val unit: Unit) extends AnyVal {
    def apply[B, S](execute: PartialFunction[Event[A], Operation[B, Unit]]): HttpChannel[Any, Nothing, A, B] =
      HttpChannel(()) { (_, event) =>
        if (execute.isDefinedAt(event)) HttpResult.succeed(execute(event))
        else HttpResult.empty
      }
  }

  final class MkCollectWith[A](val unit: Unit) extends AnyVal {
    def apply[B, S](s: S)(
      execute: PartialFunction[(S, Event[A]), Operation[B, S]],
    ): HttpChannel[Any, Nothing, A, B] =
      HttpChannel(s) { (s, event) =>
        if (execute.isDefinedAt((s, event))) HttpResult.succeed(execute(s -> event))
        else HttpResult.empty
      }
  }

  def empty: HttpChannel.Aux[Any, Nothing, Unit, Any, Nothing] = collectM(_ => HttpResult.succeed(Operation.empty))

  /**
   * Compiles a HttpChannel into a netty channel handler
   */
  private[zhttp] def compile[R, E <: Throwable](
    zExec: UnsafeChannelExecutor[R],
    channel: Http[R, E, JHttpRequest, HttpChannel[R, E, JHttpContent, JHttpObject]],
  ): JChannelHandler =
    new JChannelInboundHandlerAdapter {
      import Operation._

      private var dataChannel: HttpChannel[R, E, JHttpContent, JHttpObject] = null
      private var state: Any                                                = null

      private def executeOperation[A, S](ctx: JChannelHandlerContext, operation: Operation[A, S]): Unit = {
        operation match {
          case Write(data)          => ctx.write(data): Unit
          case Run(cb)              => cb(ctx): Unit
          case Save(s)              => state = s
          case Combine(self, other) =>
            executeOperation(ctx, self)
            executeOperation(ctx, other)
          case FMap(self, ab)       =>
            executeOperation(
              ctx,
              self match {
                case Write(data)          => Write(ab(data))
                case Combine(self, other) => Combine(self.map(ab), other.map(ab))
                case FMap(self, bc)       => self.map(bc.andThen(ab))
                case m @ Run(_)           => m
                case m @ Save(_)          => m
              },
            )
        }
      }

      private def execute(ctx: JChannelHandlerContext, event: Event[JHttpContent]): Unit = {
        dataChannel.executeWithAnyState(state, event).evaluate match {
          case HttpResult.Success(a)      => executeOperation(ctx, a)
          case HttpResult.Failure(e)      => ctx.fireExceptionCaught(e): Unit
          case HttpResult.Effect(program) => zExec.unsafeExecute(ctx, program) { _ => ??? }
          case HttpResult.Empty           => ()
        }
      }

      private def executeFirst(ctx: JChannelHandlerContext, event: JHttpRequest): Unit =
        channel.execute(event).evaluate match {
          case HttpResult.Empty           => ()
          case HttpResult.Success(a)      =>
            dataChannel = a
            state = dataChannel.initialize
            execute(ctx, Event.Register)
          case HttpResult.Failure(e)      => ctx.fireExceptionCaught(e): Unit
          case HttpResult.Effect(program) => zExec.unsafeExecute(ctx, program) { _ => ??? }
        }

      override def channelReadComplete(ctx: JChannelHandlerContext): Unit =
        execute(ctx, Event.Complete)

      override def channelRead(ctx: JChannelHandlerContext, msg: Any): Unit = {
        msg match {
          case msg: JHttpRequest => executeFirst(ctx, msg)
          case msg: JHttpContent => execute(ctx, Event.Read(msg))
          case _                 => ctx.fireExceptionCaught(new Error("Unhandled message on HttpChannel")): Unit
        }
      }

      override def channelRegistered(ctx: JChannelHandlerContext): Unit = {
        ctx.channel.config.setAutoRead(false)
        ctx.channel.read(): Unit
      }
    }
}
