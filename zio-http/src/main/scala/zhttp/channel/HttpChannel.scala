package zhttp.channel

import io.netty.channel.{ChannelInboundHandlerAdapter => JChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{HttpContent => JHttpContent, HttpObject => JHttpObject, HttpRequest => JHttpRequest}
import zhttp.core.{JChannelHandler, JChannelHandlerContext}
import zhttp.http.{Http, HttpResult}
import zhttp.service.UnsafeChannelExecutor
import zio.Queue

/**
 * Low level handle to control the flow of messages over a network channel.
 *
 * TODO: Benchmark and optimize for performance
 */

case class HttpChannel[-R, +E, -A, +B](private val channel: HttpChannel.Channel[R, E, A, B]) { self =>
  def map[C](bc: B => C): HttpChannel[R, E, A, C] = {
    HttpChannel.make(channel.initialize) { (state, event) =>
      self.channel.execute(state, event).map(_.map(bc))
    }
  }

  def contramap[X](xa: X => A): HttpChannel[R, E, X, B] =
    HttpChannel.make(channel.initialize) { (state, event) =>
      self.channel.execute(state, event.map(xa))
    }

  def orElse[R1 <: R, E1, A1 <: A, B1 >: B](other: HttpChannel[R1, E1, A1, B1]): HttpChannel[R1, E1, A1, B1] =
    HttpChannel.make((self.channel.initialize, other.channel.initialize)) { (state, event) =>
      val l = self.channel.execute(state._1, event).map(_.mapS(s => (s, state._2)))
      val r = other.channel.execute(state._2, event).map(_.mapS(s => (state._1, s)))
      l.orElse(r)
    }

  // This API is impossible to implement
  def plug[A1 <: A, B1 >: B](in: Queue[A1], out: Queue[B1]): HttpChannel[R, E, A1, B1] = ???
}

object HttpChannel {
  type Process[-R, +E, S, -A, +B] = (S, Event[A]) => HttpResult[R, E, Operation[B, S]]

  def make[R, E, A, B, S](state: S)(exe: Process[R, E, S, A, B]): HttpChannel[R, E, A, B] =
    HttpChannel(Channel.make(state)(exe))

  def collectM[R, E, A, B](exe: Event[A] => HttpResult[R, E, Operation[B, Unit]]): HttpChannel[R, E, A, B] =
    HttpChannel.make(())((_, event) => exe(event))

  def collect[A]: MkCollect[A] = new MkCollect[A](())

  def collectWith[A]: MkCollectWith[A] = new MkCollectWith[A](())

  def empty: HttpChannel[Any, Nothing, Any, Nothing] = HttpChannel.collectM(_ => HttpResult.empty)

  /**
   * Compiles a HttpChannel into a netty channel handler
   */
  private[zhttp] def compile[R, E <: Throwable](
    zExec: UnsafeChannelExecutor[R],
    http: Http[R, E, JHttpRequest, HttpChannel[R, E, JHttpContent, JHttpObject]],
  ): JChannelHandler =
    new JChannelInboundHandlerAdapter {
      import Operation._

      private var dataChannel: HttpChannel[R, E, JHttpContent, JHttpObject] = _
      private var state: Any                                                = _

      private def executeOp[A, S](ctx: JChannelHandlerContext, operation: Operation[A, S]): Unit = {
        operation match {
          case Write(data)          => ctx.write(data): Unit
          case Run(cb)              => cb(ctx): Unit
          case Save(s)              => state = s
          case Combine(self, other) =>
            executeOp(ctx, self)
            executeOp(ctx, other)
          case BiMap(self, ab, st)  =>
            executeOp(
              ctx,
              self match {
                case Save(state)           => Save(st(state))
                case Write(data)           => Write(ab(data))
                case Combine(self, other)  => Combine(self.bimap(ab, st), other.bimap(ab, st))
                case BiMap(self, st0, ab0) => self.bimap(ab0.andThen(ab), st0.andThen(st))
                case m @ Run(_)            => m
              },
            )
        }
      }

      private def executeCh(ctx: JChannelHandlerContext, event: Event[JHttpContent]): Unit = {
        dataChannel.channel.executeWithAnyState(state, event).evaluate match {
          case HttpResult.Success(a)      => executeOp(ctx, a)
          case HttpResult.Failure(e)      => ctx.fireExceptionCaught(e): Unit
          case HttpResult.Effect(program) => zExec.unsafeExecute(ctx, program) { _ => ??? }
          case HttpResult.Empty           => ()
        }
      }

      private def executeH(ctx: JChannelHandlerContext, event: JHttpRequest): Unit = {
        http.execute(event).evaluate match {
          case HttpResult.Empty           => ()
          case HttpResult.Success(a)      =>
            dataChannel = a
            state = dataChannel.channel.initialize
            executeCh(ctx, Event.Register)
          case HttpResult.Failure(e)      => ctx.fireExceptionCaught(e): Unit
          case HttpResult.Effect(program) => zExec.unsafeExecute(ctx, program) { _ => ??? }
        }
      }

      override def channelReadComplete(ctx: JChannelHandlerContext): Unit = {
        executeCh(ctx, Event.Complete)
      }

      override def channelRead(ctx: JChannelHandlerContext, msg: Any): Unit = {
        msg match {
          case msg: JHttpRequest => executeH(ctx, msg)
          case msg: JHttpContent => executeCh(ctx, Event.Read(msg))
          case _                 => ctx.fireExceptionCaught(new Error("Unhandled message on HttpChannel")): Unit
        }
      }

      override def channelRegistered(ctx: JChannelHandlerContext): Unit = {
        ctx.channel.config.setAutoRead(false)
        ctx.channel.read(): Unit
      }
    }

  final class MkCollect[A](val unit: Unit) extends AnyVal {
    def apply[B, S](execute: PartialFunction[Event[A], Operation[B, Unit]]): HttpChannel[Any, Nothing, A, B] =
      HttpChannel.make(()) { (_, event) =>
        if (execute.isDefinedAt(event)) HttpResult.succeed(execute(event))
        else HttpResult.empty
      }
  }

  final class MkCollectWith[A](val unit: Unit) extends AnyVal {
    def apply[B, S](s: S)(
      execute: PartialFunction[(S, Event[A]), Operation[B, S]],
    ): HttpChannel[Any, Nothing, A, B] =
      HttpChannel.make(s) { (s, event) =>
        if (execute.isDefinedAt((s, event))) HttpResult.succeed(execute(s -> event))
        else HttpResult.empty
      }
  }

  private[zhttp] sealed trait Channel[-R, +E, -A, +B] { self =>
    type State
    def initialize: State
    def execute(state: State, event: Event[A]): HttpResult[R, E, Operation[B, State]]
    def executeWithAnyState(state: Any, event: Event[A]): HttpResult[R, E, Operation[B, State]] =
      execute(state.asInstanceOf[State], event)
  }

  object Channel {
    type Aux[-R, +E, S, -A, +B] = Channel[R, E, A, B] {
      type State = S
    }

    def make[R, E, A, B, S](state: S)(exe: Process[R, E, S, A, B]): Channel[R, E, A, B] =
      new Channel[R, E, A, B] {
        override type State = S
        override def execute(state: State, event: Event[A]): HttpResult[R, E, Operation[B, State]] = exe(state, event)
        override def initialize: State                                                             = state
      }
  }
}
