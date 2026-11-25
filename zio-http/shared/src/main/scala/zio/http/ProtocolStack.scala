/*
 * Copyright 2023 the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import scala.annotation.nowarn

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A [[zio.http.ProtocolStack]] represents a linear stack of protocol layers,
 * each of which can statefully transform incoming and outgoing values of some
 * handler.
 *
 * Protocol stacks can be thought of as a formal model of the process of
 * transforming one handler into another handler.
 *
 * Protocol stacks are designed to support precise semantics around error
 * handling. In particular, if a layer successfully processes an incoming value
 * of a handler, then that same layer will also have a chance to process the
 * outgoing value of the handler. This requirement constrains the ways in which
 * layers can fail, and the types of handlers they may be applied to.
 *
 * In particular, protocol stacks can be applied to handlers that fail with the
 * same type as their output type. When the handler itself fails, `apply`
 * returns that failure as it is: no layer's outgoing step runs on it.
 *
 * Further, a layer may only fail while it processes an incoming value, and it
 * may only fail with a value of the same type as its outgoing value. This
 * guarantees that should a layer fail, it will still produce an outgoing value
 * that is processed by all the earlier layers that successfully transformed the
 * incoming value.
 *
 * In a way, the entire design of protocol stacks is geared at ensuring layers
 * that successfully process incoming values will also have a chance to process
 * outgoing values.
 *
 * The only composition operator on protocol stacks is `++`, which simply chains
 * two stacks together. This operator is associative and has an identity (which
 * is the protocol stack that neither transforms incoming nor outgoing values,
 * and which acts as an identity when used to transform handlers).
 *
 * {{{
 * val stack: ProtocolStack[Any, Int, Int, String, String] =
 *   ProtocolStack.identity[Int, String].mapIncoming((n: Int) => n * 2).mapOutgoing((s: String) => s.toUpperCase)
 *
 * val doubled: Handler[Any, String, Int, String] = stack(Handler.fromFunction[Int](n => "n=" + n))
 * // doubled(21) succeeds with "N=42"
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/aop/protocol-stack/ ProtocolStack]]
 */
sealed trait ProtocolStack[-Env, -IncomingIn, +IncomingOut, -OutgoingIn, +OutgoingOut] { self =>
  import ProtocolStack._

  /**
   * The state that a layer's incoming step passes to its outgoing step for the
   * same value.
   */
  type State

  /**
   * Wraps `handler` with this stack: each input goes through the incoming
   * steps, then the handler, then the outgoing steps. When an incoming step
   * fails, the result is that failure after the outgoing steps of the earlier
   * layers; when the handler fails, the result is the handler's failure.
   */
  final def apply[Env1 <: Env, Err >: OutgoingOut, IncomingOut1 >: IncomingOut, OutgoingIn1 <: OutgoingIn](
    handler: Handler[Env1, Err, IncomingOut1, OutgoingIn1],
  )(implicit trace: Trace): Handler[Env1, Err, IncomingIn, OutgoingOut] =
    Handler.scoped[Env1] {
      Handler
        .fromFunctionZIO[IncomingIn] { incomingIn =>
          incoming(incomingIn).flatMap { case (state, incomingOut) =>
            handler(incomingOut).flatMap { outgoingIn =>
              outgoing(state, outgoingIn)
            }
          }
        }
    }

  private[http] def incoming(in: IncomingIn)(implicit trace: Trace): ZIO[Scope & Env, OutgoingOut, (State, IncomingOut)]

  // TODO: Make this the one true representation and delete `incoming`
  /**
   * The incoming step of this stack as a handler: it turns an incoming value
   * into the state and the value passed on, or fails with an outgoing value.
   */
  val incomingHandler: Handler[Env, OutgoingOut, IncomingIn, (State, IncomingOut)] = {
    implicit val trace: Trace = Trace.empty
    Handler.scoped[Env](Handler.fromFunctionZIO[IncomingIn](incoming(_)))
  }

  /**
   * Returns a stack that applies `f` to the incoming value after this stack's
   * incoming steps.
   */
  def mapIncoming[IncomingOut2](
    f: IncomingOut => IncomingOut2,
  ): ProtocolStack[Env, IncomingIn, IncomingOut2, OutgoingIn, OutgoingOut] =
    self ++ ProtocolStack.interceptIncomingHandler(Handler.fromFunction(f))

  /**
   * Returns a stack that applies `f` to the outgoing value after this stack's
   * outgoing steps.
   */
  def mapOutgoing[OutgoingOut2](
    f: OutgoingOut => OutgoingOut2,
  ): ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut2] =
    ProtocolStack.interceptOutgoingHandler(Handler.fromFunction(f)) ++ self

  private[http] def outgoing(state: State, in: OutgoingIn)(implicit
    trace: Trace,
  ): ZIO[Scope & Env, Nothing, OutgoingOut]

  // TODO: Make this the one true representation and delete `outgoing`
  /**
   * The outgoing step of this stack as a handler: it turns the state and an
   * outgoing value into the final outgoing value.
   */
  val outgoingHandler: Handler[Env, Nothing, (State, OutgoingIn), OutgoingOut] = {
    implicit val trace: Trace = Trace.empty
    Handler.scoped[Env] {
      Handler
        .fromFunctionZIO[(State, OutgoingIn)] { case (state, in) =>
          outgoing(state, in)
        }
    }
  }

  /**
   * Returns a stack that needs no environment, with `env` provided to its
   * steps.
   */
  def provideEnvironment(env: ZEnvironment[Env])(implicit
    trace: Trace,
  ): ProtocolStack[Any, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] =
    ProtocolStack.interceptHandlerStateful(incomingHandler.provideEnvironment(env))(
      outgoingHandler.provideEnvironment(env),
    )

  /**
   * Chains two stacks: `self` is the outer layer and `that` the inner one.
   * Incoming values go through `self` and then `that`; outgoing values go
   * through `that` and then `self`. When an incoming step of `that` fails, the
   * failure goes through the outgoing step of `self`.
   */
  final def ++[Env1 <: Env, MiddleIncoming, MiddleOutgoing](
    that: ProtocolStack[Env1, IncomingOut, MiddleIncoming, MiddleOutgoing, OutgoingIn],
  ): ProtocolStack[Env1, IncomingIn, MiddleIncoming, MiddleOutgoing, OutgoingOut] =
    Concat(self, that)
}

/** Constructors for [[ProtocolStack]]. */
@nowarn("msg=shadows")
object ProtocolStack {

  /**
   * Starts a stack that picks one of two stacks per incoming value: the one
   * given first when `predicate` is `true`, the other one otherwise. The picked
   * stack also handles the outgoing value for that input.
   */
  def cond[IncomingIn](predicate: IncomingIn => Boolean): CondBuilder[IncomingIn] = new CondBuilder(predicate)

  /**
   * Like `cond`, with an effectful predicate. A failure of the predicate fails
   * the incoming step.
   */
  def condZIO[IncomingIn]: CondZIOBuilder[IncomingIn] = new CondZIOBuilder[IncomingIn](())

  /** A stack whose incoming step always fails with `out`. */
  def fail[Incoming, OutgoingOut](out: OutgoingOut): ProtocolStack[Any, Incoming, Incoming, OutgoingOut, OutgoingOut] =
    interceptIncomingHandler[Any, Incoming, Incoming, OutgoingOut](Handler.fail(out))

  /**
   * A stack whose incoming step always fails with `f` applied to the incoming
   * value.
   */
  def failWith[Incoming, OutgoingOut](
    f: Incoming => OutgoingOut,
  )(implicit trace: Trace): ProtocolStack[Any, Incoming, Incoming, OutgoingOut, OutgoingOut] =
    interceptIncomingHandler[Any, Incoming, Incoming, OutgoingOut](Handler.fromFunctionZIO(in => ZIO.fail(f(in))))

  /** A stack that passes incoming and outgoing values through unchanged. */
  def identity[I, O]: ProtocolStack[Any, I, I, O, O] = interceptIncomingHandler(Handler.identity)

  /**
   * A stack with one layer: `incoming0` transforms incoming values (or fails
   * with an outgoing value), and `outgoing0` transforms outgoing values.
   */
  def interceptHandler[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut](
    incoming0: Handler[Env, OutgoingOut, IncomingIn, IncomingOut],
  )(
    outgoing0: Handler[Env, Nothing, OutgoingIn, OutgoingOut],
  )(implicit trace: Trace): ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] =
    interceptHandlerStateful(incoming0.map(((), _)))(outgoing0.contramap[(Unit, OutgoingIn)](_._2))

  /**
   * A stack with one stateful layer: `incoming0` turns an incoming value into a
   * state and the value passed on, and `outgoing0` receives that state together
   * with the outgoing value.
   */
  def interceptHandlerStateful[Env, State0, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut](
    incoming0: Handler[Env, OutgoingOut, IncomingIn, (State0, IncomingOut)],
  )(
    outgoing0: Handler[Env, Nothing, (State0, OutgoingIn), OutgoingOut],
  ): ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] =
    IncomingOutgoing(incoming0, outgoing0)

  /**
   * A stack whose layer transforms incoming values with `handler` and passes
   * outgoing values through. A failure of `handler` becomes the outgoing value.
   */
  def interceptIncomingHandler[Env, IncomingIn, IncomingOut, Outgoing](
    handler: Handler[Env, Outgoing, IncomingIn, IncomingOut],
  ): ProtocolStack[Env, IncomingIn, IncomingOut, Outgoing, Outgoing] =
    Incoming(handler)

  /**
   * A stack whose layer passes incoming values through and transforms outgoing
   * values with `handler`.
   */
  def interceptOutgoingHandler[Env, Incoming, OutgoingIn, OutgoingOut](
    handler: Handler[Env, Nothing, OutgoingIn, OutgoingOut],
  ): ProtocolStack[Env, Incoming, Incoming, OutgoingIn, OutgoingOut] =
    Outgoing(handler)

  private[http] final case class Incoming[Env, IncomingIn, IncomingOut, Outgoing](
    handler: Handler[Env, Outgoing, IncomingIn, IncomingOut],
  ) extends ProtocolStack[Env, IncomingIn, IncomingOut, Outgoing, Outgoing] {
    type State = Unit

    def incoming(in: IncomingIn)(implicit trace: Trace): ZIO[Scope & Env, Outgoing, (State, IncomingOut)] =
      handler(in).map(() -> _)

    def outgoing(state: State, in: Outgoing)(implicit trace: Trace): ZIO[Env, Nothing, Outgoing] =
      Exit.succeed(in)
  }
  private[http] final case class Outgoing[Env, Incoming, OutgoingIn, OutgoingOut](
    handler: Handler[Env, Nothing, OutgoingIn, OutgoingOut],
  ) extends ProtocolStack[Env, Incoming, Incoming, OutgoingIn, OutgoingOut] {
    type State = Unit

    def incoming(in: Incoming)(implicit trace: Trace): ZIO[Scope & Env, OutgoingOut, (State, Incoming)] =
      Exit.succeed(() -> in)

    def outgoing(state: State, in: OutgoingIn)(implicit trace: Trace): ZIO[Scope & Env, Nothing, OutgoingOut] =
      handler(in)
  }
  private[http] final case class Concat[
    Env,
    IncomingIn,
    IncomingOut,
    OutgoingIn,
    OutgoingOut,
    MiddleIncoming,
    MiddleOutgoing,
  ](
    left: ProtocolStack[Env, IncomingIn, MiddleIncoming, MiddleOutgoing, OutgoingOut],
    right: ProtocolStack[Env, MiddleIncoming, IncomingOut, OutgoingIn, MiddleOutgoing],
  ) extends ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] {
    type State = (left.State, right.State)

    def incoming(in: IncomingIn)(implicit trace: Trace): ZIO[Scope & Env, OutgoingOut, (State, IncomingOut)] =
      left.incoming(in).flatMap { case (leftState, middleIn) =>
        right.incoming(middleIn).catchAll(out => left.outgoing(leftState, out).flip).map {
          case (rightState, incomingOut) => (leftState -> rightState) -> incomingOut
        }
      }

    def outgoing(state: State, in: OutgoingIn)(implicit trace: Trace): ZIO[Scope & Env, Nothing, OutgoingOut] =
      right.outgoing(state._2, in).flatMap { middleOut =>
        left.outgoing(state._1, middleOut)
      }
  }
  private[http] final case class IncomingOutgoing[Env, State0, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut](
    incoming0: Handler[Env, OutgoingOut, IncomingIn, (State0, IncomingOut)],
    outgoing0: Handler[Env, Nothing, (State0, OutgoingIn), OutgoingOut],
  ) extends ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] {
    type State = State0

    def incoming(in: IncomingIn)(implicit trace: Trace): ZIO[Scope & Env, OutgoingOut, (State, IncomingOut)] =
      incoming0(in)

    def outgoing(state: State, in: OutgoingIn)(implicit trace: Trace): ZIO[Scope & Env, Nothing, OutgoingOut] =
      outgoing0((state, in))
  }
  private[http] final case class Cond[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut](
    predicate: IncomingIn => Boolean,
    ifTrue: ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
    ifFalse: ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
  ) extends ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] {
    type State = Either[ifTrue.State, ifFalse.State]

    def incoming(in: IncomingIn)(implicit trace: Trace): ZIO[Scope & Env, OutgoingOut, (State, IncomingOut)] =
      if (predicate(in)) ifTrue.incoming(in).map { case (state, out) => (Left(state), out) }
      else ifFalse.incoming(in).map { case (state, out) => (Right(state), out) }

    def outgoing(state: State, in: OutgoingIn)(implicit trace: Trace): ZIO[Scope & Env, Nothing, OutgoingOut] =
      state match {
        case Left(state)  => ifTrue.outgoing(state, in)
        case Right(state) => ifFalse.outgoing(state, in)
      }
  }
  private[http] final case class CondZIO[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut](
    predicate: IncomingIn => ZIO[Env, OutgoingOut, Boolean],
    ifTrue: ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
    ifFalse: ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
  ) extends ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] {
    type State = Either[ifTrue.State, ifFalse.State]

    def incoming(in: IncomingIn)(implicit trace: Trace): ZIO[Scope & Env, OutgoingOut, (State, IncomingOut)] =
      predicate(in).flatMap {
        case true  => ifTrue.incoming(in).map { case (state, out) => (Left(state), out) }
        case false => ifFalse.incoming(in).map { case (state, out) => (Right(state), out) }
      }

    def outgoing(state: State, in: OutgoingIn)(implicit trace: Trace): ZIO[Scope & Env, Nothing, OutgoingOut] =
      state match {
        case Left(state)  => ifTrue.outgoing(state, in)
        case Right(state) => ifFalse.outgoing(state, in)
      }
  }

  /** The second step of `cond`: takes the two stacks to choose between. */
  class CondBuilder[IncomingIn](val predicate: IncomingIn => Boolean) extends AnyVal {

    /** Uses `ifTrue` when the predicate holds and `ifFalse` otherwise. */
    def apply[Env, IncomingOut, OutgoingIn, OutgoingOut](
      ifTrue: ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
      ifFalse: ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
    ): ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] =
      Cond(predicate, ifTrue, ifFalse)
  }

  /** The second step of `condZIO`: takes the effectful predicate. */
  class CondZIOBuilder[IncomingIn](val dummy: Unit) extends AnyVal {

    /** Sets the effectful predicate. */
    def apply[Env, Err](predicate: IncomingIn => ZIO[Env, Err, Boolean]): CondZIOBuilder1[Env, IncomingIn, Err] =
      new CondZIOBuilder1(predicate)
  }

  /** The last step of `condZIO`: takes the two stacks to choose between. */
  class CondZIOBuilder1[Env, IncomingIn, Err](val predicate: IncomingIn => ZIO[Env, Err, Boolean]) extends AnyVal {

    /**
     * Uses `ifTrue` when the predicate succeeds with `true` and `ifFalse`
     * otherwise.
     */
    def apply[Env1 <: Env, IncomingOut, OutgoingIn, OutgoingOut >: Err](
      ifTrue: ProtocolStack[Env1, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
      ifFalse: ProtocolStack[Env1, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
    ): ProtocolStack[Env1, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] =
      CondZIO(predicate, ifTrue, ifFalse)
  }
}
