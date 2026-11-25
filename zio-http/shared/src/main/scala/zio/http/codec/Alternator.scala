/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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

package zio.http.codec

import zio.ZNothing
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * An alternator is a type class that computes the result type of two
 * alternatives, `L` and `R`, and converts values to and from it. The result is
 * `Either[L, R]` in general, `A` when both sides are `A`, and the other side's
 * type when one side is `ZNothing`. `HttpCodec.|` and `RichTextCodec.|` use it
 * to compose the type parameters of [[zio.http.codec.HttpCodec]] and
 * [[zio.http.codec.RichTextCodec]].
 */
sealed trait Alternator[L, R] {

  /** The result type of the two alternatives. */
  type Out

  /** Wraps a value of the left alternative into `Out`. */
  def left(l: L): Out

  /** Wraps a value of the right alternative into `Out`. */
  def right(r: R): Out

  /**
   * Returns the left value inside `out`, or `None` when `out` holds a right
   * value.
   */
  def unleft(out: Out): Option[L]

  /**
   * Returns the right value inside `out`, or `None` when `out` holds a left
   * value.
   */
  def unright(out: Out): Option[R]

  /**
   * Returns `true` for the `leftRightEqual` instance, which is chosen
   * implicitly when `L` and `R` are the same type, and `false` for the other
   * instances.
   */
  def isSameType: Boolean = false
}

/** Instances of [[Alternator]], tried in priority order. */
object Alternator extends AlternatorLowPriority1 {

  /** An [[Alternator]] whose `Out` type is `Out0`. */
  type WithOut[L, R, Out0] = Alternator[L, R] { type Out = Out0 }

  /**
   * The alternator for two alternatives of the same type `A`, whose result is
   * `A`. `unleft` and `unright` both return the value, so `HttpCodec.|` encodes
   * a value with the left codec, unless the accepted media types match only the
   * body of the right codec.
   */
  implicit def leftRightEqual[A]: Alternator.WithOut[A, A, A] =
    new Alternator[A, A] {
      type Out = A

      def left(l: A): Out = l

      def right(r: A): Out = r

      def unleft(out: Out): Option[A] = Some(out)

      def unright(out: Out): Option[A] = Some(out)

      override def isSameType: Boolean = true
    }
}

/** Lower-priority [[Alternator]] instances: a `ZNothing` left side. */
trait AlternatorLowPriority1 extends AlternatorLowPriority2 {

  /**
   * The alternator for a `ZNothing` left side, whose result is the right type
   * `A`.
   */
  implicit def leftEmpty[A]: Alternator.WithOut[ZNothing, A, A] =
    (new Alternator[Any, A] {
      type Out = A

      def left(l: Any): Out = l.asInstanceOf[Out]

      def right(r: A): Out = r

      def unleft(out: Out): Option[Any] = None

      def unright(out: Out): Option[A] = Some(out)
    }).asInstanceOf[Alternator.WithOut[ZNothing, A, A]] // Work around compiler bug
}

/** Lower-priority [[Alternator]] instances: a `ZNothing` right side. */
trait AlternatorLowPriority2 extends AlternatorLowPriority3 {

  /**
   * The alternator for a `ZNothing` right side, whose result is the left type
   * `A`.
   */
  implicit def rightEmpty[A]: Alternator.WithOut[A, ZNothing, A] =
    (new Alternator[A, Any] {
      type Out = A

      def left(l: A): Out = l

      def right(r: Any): Out = r.asInstanceOf[Out]

      def unleft(out: Out): Option[A] = Some(out)

      def unright(out: Out): Option[Any] = None
    }).asInstanceOf[Alternator.WithOut[A, ZNothing, A]] // Work around compiler bug
}

/** The lowest-priority [[Alternator]] instance: `Either`. */
trait AlternatorLowPriority3 {

  /**
   * The alternator for two different types `A` and `B`, whose result is
   * `Either[A, B]`.
   */
  implicit def either[A, B]: Alternator.WithOut[A, B, Either[A, B]] =
    new Alternator[A, B] {
      type Out = Either[A, B]

      def left(l: A): Out = Left(l)

      def right(r: B): Out = Right(r)

      def unleft(out: Out): Option[A] = out.left.toOption

      def unright(out: Out): Option[B] = out.toOption
    }
}
