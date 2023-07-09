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

import zio._

/**
 * A [[zio.http.ProtocolStack]] represents a linear stack of protocol layers,
 * each of which can statefully transform incoming and outgoing values of a
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
 * same type as their output type. This guarantees that should a handler fail,
 * it will still produce an outgoing value that can be processed by all the
 * layers that successfully transformed the incoming value.
 *
 * Further, a layer may only fail while it processes an incoming value, and it
 * may only value with the same type as its outgoing value. This guarantees that
 * should a layer fail, it will still produce an outgoing value that can be
 * processed by all the earlier layers that successfully transformed the
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
 */
sealed trait ProtocolStack[-Env, -IncomingIn, +IncomingOut, -OutgoingIn, +OutgoingOut] { self =>
  import ProtocolStack._

  type State

  final def apply[Env1 <: Env, Err >: OutgoingOut, IncomingOut1 >: IncomingOut, OutgoingIn1 <: OutgoingIn](
    handler: Handler[Env1, Err, IncomingOut1, OutgoingIn1],
  ): Handler[Env1, Err, IncomingIn, OutgoingOut] =
    Handler.fromFunctionZIO[IncomingIn] { incomingIn =>
      incoming(incomingIn).flatMap { case (state, incomingOut) =>
        handler(incomingOut).flatMap { outgoingIn =>
          outgoing(state, outgoingIn)
        }
      }
    }

  def incoming(in: IncomingIn): ZIO[Env, OutgoingOut, (State, IncomingOut)]

  def mapIncoming[IncomingOut2](
    f: IncomingOut => IncomingOut2,
  ): ProtocolStack[Env, IncomingIn, IncomingOut2, OutgoingIn, OutgoingOut] =
    self ++ ProtocolStack.interceptIncomingHandler(Handler.fromFunction(f))

  def mapOutgoing[OutgoingOut2](
    f: OutgoingOut => OutgoingOut2,
  ): ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut2] =
    ProtocolStack.interceptOutgoingHandler(Handler.fromFunction(f)) ++ self

  def outgoing(state: State, in: OutgoingIn): ZIO[Env, Nothing, OutgoingOut]

  final def ++[Env1 <: Env, MiddleIncoming, MiddleOutgoing](
    that: ProtocolStack[Env1, IncomingOut, MiddleIncoming, MiddleOutgoing, OutgoingIn],
  ): ProtocolStack[Env1, IncomingIn, MiddleIncoming, MiddleOutgoing, OutgoingOut] =
    Concat(self, that)
}
object ProtocolStack                                                                   {
  def cond[IncomingIn](predicate: IncomingIn => Boolean): CondBuilder[IncomingIn] = new CondBuilder(predicate)

  def condZIO[IncomingIn]: CondZIOBuilder[IncomingIn] = new CondZIOBuilder[IncomingIn](())

  def fail[Incoming, OutgoingOut](out: OutgoingOut): ProtocolStack[Any, Incoming, Incoming, OutgoingOut, OutgoingOut] =
    interceptIncomingHandler[Any, Incoming, Incoming, OutgoingOut](Handler.fail(out))

  def failWith[Incoming, OutgoingOut](
    f: Incoming => OutgoingOut,
  )(implicit trace: Trace): ProtocolStack[Any, Incoming, Incoming, OutgoingOut, OutgoingOut] =
    interceptIncomingHandler[Any, Incoming, Incoming, OutgoingOut](Handler.fromFunctionZIO(in => ZIO.fail(f(in))))

  def identity[I, O]: ProtocolStack[Any, I, I, O, O] = interceptIncomingHandler(Handler.identity)

  def interceptHandler[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut](
    incoming0: Handler[Env, OutgoingOut, IncomingIn, IncomingOut],
  )(
    outgoing0: Handler[Env, Nothing, OutgoingIn, OutgoingOut],
  ): ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] =
    interceptHandlerStateful(incoming0.map(((), _)))(outgoing0.contramap[(Unit, OutgoingIn)](_._2))

  def interceptHandlerStateful[Env, State0, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut](
    incoming0: Handler[Env, OutgoingOut, IncomingIn, (State0, IncomingOut)],
  )(
    outgoing0: Handler[Env, Nothing, (State0, OutgoingIn), OutgoingOut],
  ): ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] =
    IncomingOutgoing(incoming0, outgoing0)

  def interceptIncomingHandler[Env, IncomingIn, IncomingOut, Outgoing](
    handler: Handler[Env, Outgoing, IncomingIn, IncomingOut],
  ): ProtocolStack[Env, IncomingIn, IncomingOut, Outgoing, Outgoing] =
    Incoming(handler)

  def interceptOutgoingHandler[Env, Incoming, OutgoingIn, OutgoingOut](
    handler: Handler[Env, Nothing, OutgoingIn, OutgoingOut],
  ): ProtocolStack[Env, Incoming, Incoming, OutgoingIn, OutgoingOut] =
    Outgoing(handler)

  private[http] final case class Incoming[Env, IncomingIn, IncomingOut, Outgoing](
    handler: Handler[Env, Outgoing, IncomingIn, IncomingOut],
  ) extends ProtocolStack[Env, IncomingIn, IncomingOut, Outgoing, Outgoing] {
    type State = Unit

    def incoming(in: IncomingIn): ZIO[Env, Outgoing, (State, IncomingOut)] =
      handler(in).map(() -> _)

    def outgoing(state: State, in: Outgoing): ZIO[Env, Nothing, Outgoing] =
      Exit.succeed(in)
  }
  private[http] final case class Outgoing[Env, Incoming, OutgoingIn, OutgoingOut](
    handler: Handler[Env, Nothing, OutgoingIn, OutgoingOut],
  ) extends ProtocolStack[Env, Incoming, Incoming, OutgoingIn, OutgoingOut] {
    type State = Unit

    def incoming(in: Incoming): ZIO[Env, OutgoingOut, (State, Incoming)] = Exit.succeed(() -> in)

    def outgoing(state: State, in: OutgoingIn): ZIO[Env, Nothing, OutgoingOut] =
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

    def incoming(in: IncomingIn): ZIO[Env, OutgoingOut, (State, IncomingOut)] =
      left.incoming(in).flatMap { case (leftState, middleIn) =>
        right.incoming(middleIn).catchAll(out => left.outgoing(leftState, out).flip).map {
          case (rightState, incomingOut) => (leftState -> rightState) -> incomingOut
        }
      }

    def outgoing(state: State, in: OutgoingIn): ZIO[Env, Nothing, OutgoingOut] =
      right.outgoing(state._2, in).flatMap { middleOut =>
        left.outgoing(state._1, middleOut)
      }
  }
  private[http] final case class IncomingOutgoing[Env, State0, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut](
    incoming0: Handler[Env, OutgoingOut, IncomingIn, (State0, IncomingOut)],
    outgoing0: Handler[Env, Nothing, (State0, OutgoingIn), OutgoingOut],
  ) extends ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] {
    type State = State0

    def incoming(in: IncomingIn): ZIO[Env, OutgoingOut, (State, IncomingOut)] = incoming0(in)

    def outgoing(state: State, in: OutgoingIn): ZIO[Env, Nothing, OutgoingOut] = outgoing0(state, in)
  }
  private[http] final case class Cond[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut](
    predicate: IncomingIn => Boolean,
    ifTrue: ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
    ifFalse: ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
  ) extends ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] {
    type State = Either[ifTrue.State, ifFalse.State]

    def incoming(in: IncomingIn): ZIO[Env, OutgoingOut, (State, IncomingOut)] =
      if (predicate(in)) ifTrue.incoming(in).map { case (state, out) => (Left(state), out) }
      else ifFalse.incoming(in).map { case (state, out) => (Right(state), out) }

    def outgoing(state: State, in: OutgoingIn): ZIO[Env, Nothing, OutgoingOut] =
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

    def incoming(in: IncomingIn): ZIO[Env, OutgoingOut, (State, IncomingOut)] =
      predicate(in).flatMap {
        case true  => ifTrue.incoming(in).map { case (state, out) => (Left(state), out) }
        case false => ifFalse.incoming(in).map { case (state, out) => (Right(state), out) }
      }

    def outgoing(state: State, in: OutgoingIn): ZIO[Env, Nothing, OutgoingOut] =
      state match {
        case Left(state)  => ifTrue.outgoing(state, in)
        case Right(state) => ifFalse.outgoing(state, in)
      }
  }

  class CondBuilder[IncomingIn](val predicate: IncomingIn => Boolean) extends AnyVal {
    def apply[Env, IncomingOut, OutgoingIn, OutgoingOut](
      ifTrue: ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
      ifFalse: ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
    ): ProtocolStack[Env, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] =
      Cond(predicate, ifTrue, ifFalse)
  }

  class CondZIOBuilder[IncomingIn](val dummy: Unit) extends AnyVal {
    def apply[Env, Err](predicate: IncomingIn => ZIO[Env, Err, Boolean]): CondZIOBuilder1[Env, IncomingIn, Err] =
      new CondZIOBuilder1(predicate)
  }

  class CondZIOBuilder1[Env, IncomingIn, Err](val predicate: IncomingIn => ZIO[Env, Err, Boolean]) extends AnyVal {
    def apply[Env1 <: Env, IncomingOut, OutgoingIn, OutgoingOut >: Err](
      ifTrue: ProtocolStack[Env1, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
      ifFalse: ProtocolStack[Env1, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut],
    ): ProtocolStack[Env1, IncomingIn, IncomingOut, OutgoingIn, OutgoingOut] =
      CondZIO(predicate, ifTrue, ifFalse)
  }
}
