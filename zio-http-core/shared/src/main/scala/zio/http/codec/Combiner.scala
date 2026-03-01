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

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A combiner is a type class responsible for combining invariant type
 * parameters using a tuple. It is used to compose the parameters of the
 * [[zio.http.codec.HttpCodec]] data type.
 */
sealed trait Combiner[L, R] {
  type Out

  def combine(l: L, r: R): Out

  def separate(out: Out): (L, R)

}

object Combiner extends CombinerLowPriority1 {
  type WithOut[L, R, Out0] = Combiner[L, R] { type Out = Out0 }

  implicit def leftUnit[A]: Combiner.WithOut[Unit, A, A] =
    new Combiner[Unit, A] {
      type Out = A

      def combine(l: Unit, r: A): A = r

      def separate(out: A): (Unit, A) = ((), out)
    }
}

trait CombinerLowPriority1 extends CombinerLowPriority2 {
  implicit def rightUnit[A]: Combiner.WithOut[A, Unit, A] =
    new Combiner[A, Unit] {
      type Out = A

      def combine(l: A, r: Unit): A = l

      def separate(out: A): (A, Unit) = (out, ())
    }
}

trait CombinerLowPriority2 extends CombinerLowPriority3 {
  implicit def combine2L2R[L1, L2, R1, R2]: Combiner.WithOut[(L1, L2), (R1, R2), (L1, L2, R1, R2)] =
    new Combiner[(L1, L2), (R1, R2)] {
      type Out = (L1, L2, R1, R2)

      def combine(l: (L1, L2), r: (R1, R2)): (L1, L2, R1, R2) = (l._1, l._2, r._1, r._2)

      def separate(out: (L1, L2, R1, R2)): ((L1, L2), (R1, R2)) = ((out._1, out._2), (out._3, out._4))
    }

  implicit def combin3L2R[L1, L2, L3, R1, R2]: Combiner.WithOut[(L1, L2, L3), (R1, R2), (L1, L2, L3, R1, R2)] =
    new Combiner[(L1, L2, L3), (R1, R2)] {
      type Out = (L1, L2, L3, R1, R2)

      def combine(l: (L1, L2, L3), r: (R1, R2)): (L1, L2, L3, R1, R2) = (l._1, l._2, l._3, r._1, r._2)

      def separate(out: (L1, L2, L3, R1, R2)): ((L1, L2, L3), (R1, R2)) =
        ((out._1, out._2, out._3), (out._4, out._5))
    }

  implicit def combine4L2R[L1, L2, L3, L4, R1, R2]
    : Combiner.WithOut[(L1, L2, L3, L4), (R1, R2), (L1, L2, L3, L4, R1, R2)] =
    new Combiner[(L1, L2, L3, L4), (R1, R2)] {
      type Out = (L1, L2, L3, L4, R1, R2)

      def combine(l: (L1, L2, L3, L4), r: (R1, R2)): (L1, L2, L3, L4, R1, R2) =
        (l._1, l._2, l._3, l._4, r._1, r._2)

      def separate(out: (L1, L2, L3, L4, R1, R2)): ((L1, L2, L3, L4), (R1, R2)) =
        ((out._1, out._2, out._3, out._4), (out._5, out._6))
    }

  implicit def combine5L2R[L1, L2, L3, L4, L5, R1, R2]
    : Combiner.WithOut[(L1, L2, L3, L4, L5), (R1, R2), (L1, L2, L3, L4, L5, R1, R2)] =
    new Combiner[(L1, L2, L3, L4, L5), (R1, R2)] {
      type Out = (L1, L2, L3, L4, L5, R1, R2)

      def combine(l: (L1, L2, L3, L4, L5), r: (R1, R2)): (L1, L2, L3, L4, L5, R1, R2) =
        (l._1, l._2, l._3, l._4, l._5, r._1, r._2)

      def separate(out: (L1, L2, L3, L4, L5, R1, R2)): ((L1, L2, L3, L4, L5), (R1, R2)) =
        ((out._1, out._2, out._3, out._4, out._5), (out._6, out._7))
    }

  implicit def combine6L2R[L1, L2, L3, L4, L5, L6, R1, R2]
    : Combiner.WithOut[(L1, L2, L3, L4, L5, L6), (R1, R2), (L1, L2, L3, L4, L5, L6, R1, R2)] =
    new Combiner[(L1, L2, L3, L4, L5, L6), (R1, R2)] {
      type Out = (L1, L2, L3, L4, L5, L6, R1, R2)

      def combine(l: (L1, L2, L3, L4, L5, L6), r: (R1, R2)): (L1, L2, L3, L4, L5, L6, R1, R2) =
        (l._1, l._2, l._3, l._4, l._5, l._6, r._1, r._2)

      def separate(out: (L1, L2, L3, L4, L5, L6, R1, R2)): ((L1, L2, L3, L4, L5, L6), (R1, R2)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6), (out._7, out._8))
    }

  implicit def combine7L2R[L1, L2, L3, L4, L5, L6, L7, R1, R2]
    : Combiner.WithOut[(L1, L2, L3, L4, L5, L6, L7), (R1, R2), (L1, L2, L3, L4, L5, L6, L7, R1, R2)] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7), (R1, R2)] {
      type Out = (L1, L2, L3, L4, L5, L6, L7, R1, R2)

      def combine(l: (L1, L2, L3, L4, L5, L6, L7), r: (R1, R2)): (L1, L2, L3, L4, L5, L6, L7, R1, R2) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r._1, r._2)

      def separate(out: (L1, L2, L3, L4, L5, L6, L7, R1, R2)): ((L1, L2, L3, L4, L5, L6, L7), (R1, R2)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7), (out._8, out._9))
    }

  implicit def combine8L2R[L1, L2, L3, L4, L5, L6, L7, L8, R1, R2]
    : Combiner.WithOut[(L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2), (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2)] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2)] {
      type Out = (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2)

      def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8), r: (R1, R2)): (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r._1, r._2)

      def separate(out: (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2)): ((L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8), (out._9, out._10))
    }

  implicit def combine9L2R[L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2]
    : Combiner.WithOut[(L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2), (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2)] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2)] {
      type Out = (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2)

      def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8, L9), r: (R1, R2)): (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r._1, r._2)

      def separate(
        out: (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2),
      ): ((L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9), (out._10, out._11))
    }

  implicit def combine2L3R[L1, L2, R1, R2, R3]: Combiner.WithOut[(L1, L2), (R1, R2, R3), (L1, L2, R1, R2, R3)] =
    new Combiner[(L1, L2), (R1, R2, R3)] {
      type Out = (L1, L2, R1, R2, R3)

      def combine(l: (L1, L2), r: (R1, R2, R3)): (L1, L2, R1, R2, R3) =
        (l._1, l._2, r._1, r._2, r._3)

      def separate(out: (L1, L2, R1, R2, R3)): ((L1, L2), (R1, R2, R3)) =
        ((out._1, out._2), (out._3, out._4, out._5))
    }

  implicit def combine3L3R[L1, L2, L3, R1, R2, R3]
    : Combiner.WithOut[(L1, L2, L3), (R1, R2, R3), (L1, L2, L3, R1, R2, R3)] =
    new Combiner[(L1, L2, L3), (R1, R2, R3)] {
      type Out = (L1, L2, L3, R1, R2, R3)

      def combine(l: (L1, L2, L3), r: (R1, R2, R3)): (L1, L2, L3, R1, R2, R3) =
        (l._1, l._2, l._3, r._1, r._2, r._3)

      def separate(out: (L1, L2, L3, R1, R2, R3)): ((L1, L2, L3), (R1, R2, R3)) =
        ((out._1, out._2, out._3), (out._4, out._5, out._6))
    }

  implicit def combine4L3R[L1, L2, L3, L4, R1, R2, R3]
    : Combiner.WithOut[(L1, L2, L3, L4), (R1, R2, R3), (L1, L2, L3, L4, R1, R2, R3)] =
    new Combiner[(L1, L2, L3, L4), (R1, R2, R3)] {
      type Out = (L1, L2, L3, L4, R1, R2, R3)

      def combine(l: (L1, L2, L3, L4), r: (R1, R2, R3)): (L1, L2, L3, L4, R1, R2, R3) =
        (l._1, l._2, l._3, l._4, r._1, r._2, r._3)

      def separate(out: (L1, L2, L3, L4, R1, R2, R3)): ((L1, L2, L3, L4), (R1, R2, R3)) =
        ((out._1, out._2, out._3, out._4), (out._5, out._6, out._7))
    }

  implicit def combine5L3R[L1, L2, L3, L4, L5, R1, R2, R3]
    : Combiner.WithOut[(L1, L2, L3, L4, L5), (R1, R2, R3), (L1, L2, L3, L4, L5, R1, R2, R3)] =
    new Combiner[(L1, L2, L3, L4, L5), (R1, R2, R3)] {
      type Out = (L1, L2, L3, L4, L5, R1, R2, R3)

      def combine(l: (L1, L2, L3, L4, L5), r: (R1, R2, R3)): (L1, L2, L3, L4, L5, R1, R2, R3) =
        (l._1, l._2, l._3, l._4, l._5, r._1, r._2, r._3)

      def separate(out: (L1, L2, L3, L4, L5, R1, R2, R3)): ((L1, L2, L3, L4, L5), (R1, R2, R3)) =
        ((out._1, out._2, out._3, out._4, out._5), (out._6, out._7, out._8))
    }

  implicit def combine6L3R[L1, L2, L3, L4, L5, L6, R1, R2, R3]
    : Combiner.WithOut[(L1, L2, L3, L4, L5, L6), (R1, R2, R3), (L1, L2, L3, L4, L5, L6, R1, R2, R3)] =
    new Combiner[(L1, L2, L3, L4, L5, L6), (R1, R2, R3)] {
      type Out = (L1, L2, L3, L4, L5, L6, R1, R2, R3)

      def combine(l: (L1, L2, L3, L4, L5, L6), r: (R1, R2, R3)): (L1, L2, L3, L4, L5, L6, R1, R2, R3) =
        (l._1, l._2, l._3, l._4, l._5, l._6, r._1, r._2, r._3)

      def separate(out: (L1, L2, L3, L4, L5, L6, R1, R2, R3)): ((L1, L2, L3, L4, L5, L6), (R1, R2, R3)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6), (out._7, out._8, out._9))
    }

  implicit def combine7L3R[L1, L2, L3, L4, L5, L6, L7, R1, R2, R3]
    : Combiner.WithOut[(L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3), (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3)] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3)] {
      type Out = (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3)

      def combine(l: (L1, L2, L3, L4, L5, L6, L7), r: (R1, R2, R3)): (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r._1, r._2, r._3)

      def separate(out: (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3)): ((L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7), (out._8, out._9, out._10))
    }

  implicit def combine8L3R[L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3]
    : Combiner.WithOut[(L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3), (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3)] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3)] {
      type Out = (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3)

      def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8), r: (R1, R2, R3)): (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r._1, r._2, r._3)

      def separate(
        out: (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3),
      ): ((L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8), (out._9, out._10, out._11))
    }

  implicit def combine9L3R[L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8, L9),
    (R1, R2, R3),
    (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3)] {
      type Out = (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3)

      def combine(
        l: (L1, L2, L3, L4, L5, L6, L7, L8, L9),
        r: (R1, R2, R3),
      ): (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r._1, r._2, r._3)

      def separate(
        out: (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3),
      ): ((L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9), (out._10, out._11, out._12))
    }

  implicit def combine2L4R[L1, L2, R1, R2, R3, R4]
    : Combiner.WithOut[(L1, L2), (R1, R2, R3, R4), (L1, L2, R1, R2, R3, R4)] =
    new Combiner[(L1, L2), (R1, R2, R3, R4)] {
      override type Out = (L1, L2, R1, R2, R3, R4)

      override def combine(l: (L1, L2), r: (R1, R2, R3, R4)): Out = (l._1, l._2, r._1, r._2, r._3, r._4)

      override def separate(out: Out): ((L1, L2), (R1, R2, R3, R4)) =
        ((out._1, out._2), (out._3, out._4, out._5, out._6))
    }

  implicit def combine3L4R[L1, L2, L3, R1, R2, R3, R4]
    : Combiner.WithOut[(L1, L2, L3), (R1, R2, R3, R4), (L1, L2, L3, R1, R2, R3, R4)] =
    new Combiner[(L1, L2, L3), (R1, R2, R3, R4)] {
      override type Out = (L1, L2, L3, R1, R2, R3, R4)

      override def combine(l: (L1, L2, L3), r: (R1, R2, R3, R4)): Out = (l._1, l._2, l._3, r._1, r._2, r._3, r._4)

      override def separate(out: Out): ((L1, L2, L3), (R1, R2, R3, R4)) =
        ((out._1, out._2, out._3), (out._4, out._5, out._6, out._7))
    }

  implicit def combine4L4R[L1, L2, L3, L4, R1, R2, R3, R4]
    : Combiner.WithOut[(L1, L2, L3, L4), (R1, R2, R3, R4), (L1, L2, L3, L4, R1, R2, R3, R4)] =
    new Combiner[(L1, L2, L3, L4), (R1, R2, R3, R4)] {
      override type Out = (L1, L2, L3, L4, R1, R2, R3, R4)

      override def combine(l: (L1, L2, L3, L4), r: (R1, R2, R3, R4)): Out =
        (l._1, l._2, l._3, l._4, r._1, r._2, r._3, r._4)

      override def separate(out: Out): ((L1, L2, L3, L4), (R1, R2, R3, R4)) =
        ((out._1, out._2, out._3, out._4), (out._5, out._6, out._7, out._8))
    }

  implicit def combine5L4R[L1, L2, L3, L4, L5, R1, R2, R3, R4]
    : Combiner.WithOut[(L1, L2, L3, L4, L5), (R1, R2, R3, R4), (L1, L2, L3, L4, L5, R1, R2, R3, R4)] =
    new Combiner[(L1, L2, L3, L4, L5), (R1, R2, R3, R4)] {
      override type Out = (L1, L2, L3, L4, L5, R1, R2, R3, R4)

      override def combine(l: (L1, L2, L3, L4, L5), r: (R1, R2, R3, R4)): Out =
        (l._1, l._2, l._3, l._4, l._5, r._1, r._2, r._3, r._4)

      override def separate(out: Out): ((L1, L2, L3, L4, L5), (R1, R2, R3, R4)) =
        ((out._1, out._2, out._3, out._4, out._5), (out._6, out._7, out._8, out._9))
    }

  implicit def combine6L4R[L1, L2, L3, L4, L5, L6, R1, R2, R3, R4]
    : Combiner.WithOut[(L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4), (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4)] =
    new Combiner[(L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4)] {
      override type Out = (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4)

      override def combine(l: (L1, L2, L3, L4, L5, L6), r: (R1, R2, R3, R4)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, r._1, r._2, r._3, r._4)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6), (out._7, out._8, out._9, out._10))
    }

  implicit def combine7L4R[L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4]
    : Combiner.WithOut[(L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4), (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4)] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7), r: (R1, R2, R3, R4)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r._1, r._2, r._3, r._4)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7), (out._8, out._9, out._10, out._11))
    }

  implicit def combine8L4R[L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8),
    (R1, R2, R3, R4),
    (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8), r: (R1, R2, R3, R4)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r._1, r._2, r._3, r._4)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8), (out._9, out._10, out._11, out._12))
    }

  implicit def combine9L4R[L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8, L9),
    (R1, R2, R3, R4),
    (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8, L9), r: (R1, R2, R3, R4)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r._1, r._2, r._3, r._4)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9), (out._10, out._11, out._12, out._13))
    }

  implicit def combine2L5R[L1, L2, R1, R2, R3, R4, R5]
    : Combiner.WithOut[(L1, L2), (R1, R2, R3, R4, R5), (L1, L2, R1, R2, R3, R4, R5)] =
    new Combiner[(L1, L2), (R1, R2, R3, R4, R5)] {
      override type Out = (L1, L2, R1, R2, R3, R4, R5)

      override def combine(l: (L1, L2), r: (R1, R2, R3, R4, R5)): Out = (l._1, l._2, r._1, r._2, r._3, r._4, r._5)

      override def separate(out: Out): ((L1, L2), (R1, R2, R3, R4, R5)) =
        ((out._1, out._2), (out._3, out._4, out._5, out._6, out._7))
    }

  implicit def combine3L5R[L1, L2, L3, R1, R2, R3, R4, R5]
    : Combiner.WithOut[(L1, L2, L3), (R1, R2, R3, R4, R5), (L1, L2, L3, R1, R2, R3, R4, R5)] =
    new Combiner[(L1, L2, L3), (R1, R2, R3, R4, R5)] {
      override type Out = (L1, L2, L3, R1, R2, R3, R4, R5)

      override def combine(l: (L1, L2, L3), r: (R1, R2, R3, R4, R5)): Out =
        (l._1, l._2, l._3, r._1, r._2, r._3, r._4, r._5)

      override def separate(out: Out): ((L1, L2, L3), (R1, R2, R3, R4, R5)) =
        ((out._1, out._2, out._3), (out._4, out._5, out._6, out._7, out._8))
    }

  implicit def combine4L5R[L1, L2, L3, L4, R1, R2, R3, R4, R5]
    : Combiner.WithOut[(L1, L2, L3, L4), (R1, R2, R3, R4, R5), (L1, L2, L3, L4, R1, R2, R3, R4, R5)] =
    new Combiner[(L1, L2, L3, L4), (R1, R2, R3, R4, R5)] {
      override type Out = (L1, L2, L3, L4, R1, R2, R3, R4, R5)

      override def combine(l: (L1, L2, L3, L4), r: (R1, R2, R3, R4, R5)): Out =
        (l._1, l._2, l._3, l._4, r._1, r._2, r._3, r._4, r._5)

      override def separate(out: Out): ((L1, L2, L3, L4), (R1, R2, R3, R4, R5)) =
        ((out._1, out._2, out._3, out._4), (out._5, out._6, out._7, out._8, out._9))
    }

  implicit def combine5L5R[L1, L2, L3, L4, L5, R1, R2, R3, R4, R5]
    : Combiner.WithOut[(L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5), (L1, L2, L3, L4, L5, R1, R2, R3, R4, R5)] =
    new Combiner[(L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5)] {
      override type Out = (L1, L2, L3, L4, L5, R1, R2, R3, R4, R5)

      override def combine(l: (L1, L2, L3, L4, L5), r: (R1, R2, R3, R4, R5)): Out =
        (l._1, l._2, l._3, l._4, l._5, r._1, r._2, r._3, r._4, r._5)

      override def separate(out: Out): ((L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5)) =
        ((out._1, out._2, out._3, out._4, out._5), (out._6, out._7, out._8, out._9, out._10))
    }

  implicit def combine6L5R[L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5]
    : Combiner.WithOut[(L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5), (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5)] =
    new Combiner[(L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5)] {
      override type Out = (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5)

      override def combine(l: (L1, L2, L3, L4, L5, L6), r: (R1, R2, R3, R4, R5)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, r._1, r._2, r._3, r._4, r._5)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6), (out._7, out._8, out._9, out._10, out._11))
    }

  implicit def combine7L5R[L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7),
    (R1, R2, R3, R4, R5),
    (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4, R5)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7), r: (R1, R2, R3, R4, R5)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r._1, r._2, r._3, r._4, r._5)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4, R5)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7), (out._8, out._9, out._10, out._11, out._12))
    }

  implicit def combine8L5R[L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8),
    (R1, R2, R3, R4, R5),
    (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4, R5)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8), r: (R1, R2, R3, R4, R5)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r._1, r._2, r._3, r._4, r._5)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4, R5)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8), (out._9, out._10, out._11, out._12, out._13))
    }

  implicit def combine9L5R[L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8, L9),
    (R1, R2, R3, R4, R5),
    (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4, R5)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8, L9), r: (R1, R2, R3, R4, R5)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r._1, r._2, r._3, r._4, r._5)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4, R5)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9),
          (out._10, out._11, out._12, out._13, out._14),
        )
    }

  implicit def combine2L6R[L1, L2, R1, R2, R3, R4, R5, R6]: Combiner.WithOut[
    (L1, L2),
    (R1, R2, R3, R4, R5, R6),
    (L1, L2, R1, R2, R3, R4, R5, R6),
  ] =
    new Combiner[(L1, L2), (R1, R2, R3, R4, R5, R6)] {
      override type Out = (L1, L2, R1, R2, R3, R4, R5, R6)

      override def combine(l: (L1, L2), r: (R1, R2, R3, R4, R5, R6)): Out =
        (l._1, l._2, r._1, r._2, r._3, r._4, r._5, r._6)

      override def separate(out: Out): ((L1, L2), (R1, R2, R3, R4, R5, R6)) =
        ((out._1, out._2), (out._3, out._4, out._5, out._6, out._7, out._8))
    }

  implicit def combine3L6R[L1, L2, L3, R1, R2, R3, R4, R5, R6]
    : Combiner.WithOut[(L1, L2, L3), (R1, R2, R3, R4, R5, R6), (L1, L2, L3, R1, R2, R3, R4, R5, R6)] =
    new Combiner[(L1, L2, L3), (R1, R2, R3, R4, R5, R6)] {
      override type Out = (L1, L2, L3, R1, R2, R3, R4, R5, R6)

      override def combine(l: (L1, L2, L3), r: (R1, R2, R3, R4, R5, R6)): Out =
        (l._1, l._2, l._3, r._1, r._2, r._3, r._4, r._5, r._6)

      override def separate(out: Out): ((L1, L2, L3), (R1, R2, R3, R4, R5, R6)) =
        ((out._1, out._2, out._3), (out._4, out._5, out._6, out._7, out._8, out._9))
    }

  implicit def combine4L6R[L1, L2, L3, L4, R1, R2, R3, R4, R5, R6]
    : Combiner.WithOut[(L1, L2, L3, L4), (R1, R2, R3, R4, R5, R6), (L1, L2, L3, L4, R1, R2, R3, R4, R5, R6)] =
    new Combiner[(L1, L2, L3, L4), (R1, R2, R3, R4, R5, R6)] {
      override type Out = (L1, L2, L3, L4, R1, R2, R3, R4, R5, R6)

      override def combine(l: (L1, L2, L3, L4), r: (R1, R2, R3, R4, R5, R6)): Out =
        (l._1, l._2, l._3, l._4, r._1, r._2, r._3, r._4, r._5, r._6)

      override def separate(out: Out): ((L1, L2, L3, L4), (R1, R2, R3, R4, R5, R6)) =
        ((out._1, out._2, out._3, out._4), (out._5, out._6, out._7, out._8, out._9, out._10))
    }

  implicit def combine5L6R[L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6]
    : Combiner.WithOut[(L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5, R6), (L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6)] =
    new Combiner[(L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5, R6)] {
      override type Out = (L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6)

      override def combine(l: (L1, L2, L3, L4, L5), r: (R1, R2, R3, R4, R5, R6)): Out =
        (l._1, l._2, l._3, l._4, l._5, r._1, r._2, r._3, r._4, r._5, r._6)

      override def separate(out: Out): ((L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5, R6)) =
        ((out._1, out._2, out._3, out._4, out._5), (out._6, out._7, out._8, out._9, out._10, out._11))
    }

  implicit def combine6L6R[L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6),
    (R1, R2, R3, R4, R5, R6),
    (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5, R6)] {
      override type Out = (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6)

      override def combine(l: (L1, L2, L3, L4, L5, L6), r: (R1, R2, R3, R4, R5, R6)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, r._1, r._2, r._3, r._4, r._5, r._6)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5, R6)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6), (out._7, out._8, out._9, out._10, out._11, out._12))
    }

  implicit def combine7L6R[L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7),
    (R1, R2, R3, R4, R5, R6),
    (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4, R5, R6)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7), r: (R1, R2, R3, R4, R5, R6)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r._1, r._2, r._3, r._4, r._5, r._6)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4, R5, R6)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7), (out._8, out._9, out._10, out._11, out._12, out._13))
    }

  implicit def combine8L6R[L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8),
    (R1, R2, R3, R4, R5, R6),
    (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4, R5, R6)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8), r: (R1, R2, R3, R4, R5, R6)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r._1, r._2, r._3, r._4, r._5, r._6)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4, R5, R6)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8),
          (out._9, out._10, out._11, out._12, out._13, out._14),
        )
    }

  implicit def combine9L6R[L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8, L9),
    (R1, R2, R3, R4, R5, R6),
    (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4, R5, R6)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8, L9), r: (R1, R2, R3, R4, R5, R6)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r._1, r._2, r._3, r._4, r._5, r._6)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4, R5, R6)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9),
          (out._10, out._11, out._12, out._13, out._14, out._15),
        )
    }

  implicit def combine2L7R[L1, L2, R1, R2, R3, R4, R5, R6, R7]
    : Combiner.WithOut[(L1, L2), (R1, R2, R3, R4, R5, R6, R7), (L1, L2, R1, R2, R3, R4, R5, R6, R7)] =
    new Combiner[(L1, L2), (R1, R2, R3, R4, R5, R6, R7)] {
      override type Out = (L1, L2, R1, R2, R3, R4, R5, R6, R7)

      override def combine(l: (L1, L2), r: (R1, R2, R3, R4, R5, R6, R7)): Out =
        (l._1, l._2, r._1, r._2, r._3, r._4, r._5, r._6, r._7)

      override def separate(out: Out): ((L1, L2), (R1, R2, R3, R4, R5, R6, R7)) =
        ((out._1, out._2), (out._3, out._4, out._5, out._6, out._7, out._8, out._9))
    }

  implicit def combine3L7R[L1, L2, L3, R1, R2, R3, R4, R5, R6, R7]
    : Combiner.WithOut[(L1, L2, L3), (R1, R2, R3, R4, R5, R6, R7), (L1, L2, L3, R1, R2, R3, R4, R5, R6, R7)] =
    new Combiner[(L1, L2, L3), (R1, R2, R3, R4, R5, R6, R7)] {
      override type Out = (L1, L2, L3, R1, R2, R3, R4, R5, R6, R7)

      override def combine(l: (L1, L2, L3), r: (R1, R2, R3, R4, R5, R6, R7)): Out =
        (l._1, l._2, l._3, r._1, r._2, r._3, r._4, r._5, r._6, r._7)

      override def separate(out: Out): ((L1, L2, L3), (R1, R2, R3, R4, R5, R6, R7)) =
        ((out._1, out._2, out._3), (out._4, out._5, out._6, out._7, out._8, out._9, out._10))
    }

  implicit def combine4L7R[L1, L2, L3, L4, R1, R2, R3, R4, R5, R6, R7]
    : Combiner.WithOut[(L1, L2, L3, L4), (R1, R2, R3, R4, R5, R6, R7), (L1, L2, L3, L4, R1, R2, R3, R4, R5, R6, R7)] =
    new Combiner[(L1, L2, L3, L4), (R1, R2, R3, R4, R5, R6, R7)] {
      override type Out = (L1, L2, L3, L4, R1, R2, R3, R4, R5, R6, R7)

      override def combine(l: (L1, L2, L3, L4), r: (R1, R2, R3, R4, R5, R6, R7)): Out =
        (l._1, l._2, l._3, l._4, r._1, r._2, r._3, r._4, r._5, r._6, r._7)

      override def separate(out: Out): ((L1, L2, L3, L4), (R1, R2, R3, R4, R5, R6, R7)) =
        ((out._1, out._2, out._3, out._4), (out._5, out._6, out._7, out._8, out._9, out._10, out._11))
    }

  implicit def combine5L7R[L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6, R7]: Combiner.WithOut[
    (L1, L2, L3, L4, L5),
    (R1, R2, R3, R4, R5, R6, R7),
    (L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6, R7),
  ] =
    new Combiner[(L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5, R6, R7)] {
      override type Out = (L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6, R7)

      override def combine(l: (L1, L2, L3, L4, L5), r: (R1, R2, R3, R4, R5, R6, R7)): Out =
        (l._1, l._2, l._3, l._4, l._5, r._1, r._2, r._3, r._4, r._5, r._6, r._7)

      override def separate(out: Out): ((L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5, R6, R7)) =
        ((out._1, out._2, out._3, out._4, out._5), (out._6, out._7, out._8, out._9, out._10, out._11, out._12))
    }

  implicit def combine6L7R[L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6, R7]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6),
    (R1, R2, R3, R4, R5, R6, R7),
    (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6, R7),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5, R6, R7)] {
      override type Out = (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6, R7)

      override def combine(l: (L1, L2, L3, L4, L5, L6), r: (R1, R2, R3, R4, R5, R6, R7)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, r._1, r._2, r._3, r._4, r._5, r._6, r._7)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5, R6, R7)) =
        ((out._1, out._2, out._3, out._4, out._5, out._6), (out._7, out._8, out._9, out._10, out._11, out._12, out._13))
    }

  implicit def combine7L7R[L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6, R7]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7),
    (R1, R2, R3, R4, R5, R6, R7),
    (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6, R7),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4, R5, R6, R7)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6, R7)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7), r: (R1, R2, R3, R4, R5, R6, R7)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r._1, r._2, r._3, r._4, r._5, r._6, r._7)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4, R5, R6, R7)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7),
          (out._8, out._9, out._10, out._11, out._12, out._13, out._14),
        )
    }

  implicit def combine8L7R[L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6, R7]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8),
    (R1, R2, R3, R4, R5, R6, R7),
    (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6, R7),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4, R5, R6, R7)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6, R7)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8), r: (R1, R2, R3, R4, R5, R6, R7)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r._1, r._2, r._3, r._4, r._5, r._6, r._7)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4, R5, R6, R7)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8),
          (out._9, out._10, out._11, out._12, out._13, out._14, out._15),
        )
    }

  implicit def combine9L7R[L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6, R7]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8, L9),
    (R1, R2, R3, R4, R5, R6, R7),
    (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6, R7),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4, R5, R6, R7)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6, R7)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8, L9), r: (R1, R2, R3, R4, R5, R6, R7)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r._1, r._2, r._3, r._4, r._5, r._6, r._7)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4, R5, R6, R7)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9),
          (out._10, out._11, out._12, out._13, out._14, out._15, out._16),
        )
    }

  implicit def combine2L8R[L1, L2, R1, R2, R3, R4, R5, R6, R7, R8]
    : Combiner.WithOut[(L1, L2), (R1, R2, R3, R4, R5, R6, R7, R8), (L1, L2, R1, R2, R3, R4, R5, R6, R7, R8)] =
    new Combiner[(L1, L2), (R1, R2, R3, R4, R5, R6, R7, R8)] {
      override type Out = (L1, L2, R1, R2, R3, R4, R5, R6, R7, R8)

      override def combine(l: (L1, L2), r: (R1, R2, R3, R4, R5, R6, R7, R8)): Out =
        (l._1, l._2, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8)

      override def separate(out: Out): ((L1, L2), (R1, R2, R3, R4, R5, R6, R7, R8)) =
        ((out._1, out._2), (out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10))
    }

  implicit def combine3L8R[L1, L2, L3, R1, R2, R3, R4, R5, R6, R7, R8]
    : Combiner.WithOut[(L1, L2, L3), (R1, R2, R3, R4, R5, R6, R7, R8), (L1, L2, L3, R1, R2, R3, R4, R5, R6, R7, R8)] =
    new Combiner[(L1, L2, L3), (R1, R2, R3, R4, R5, R6, R7, R8)] {
      override type Out = (L1, L2, L3, R1, R2, R3, R4, R5, R6, R7, R8)

      override def combine(l: (L1, L2, L3), r: (R1, R2, R3, R4, R5, R6, R7, R8)): Out =
        (l._1, l._2, l._3, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8)

      override def separate(out: Out): ((L1, L2, L3), (R1, R2, R3, R4, R5, R6, R7, R8)) =
        ((out._1, out._2, out._3), (out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11))
    }

  implicit def combine4L8R[L1, L2, L3, L4, R1, R2, R3, R4, R5, R6, R7, R8]: Combiner.WithOut[
    (L1, L2, L3, L4),
    (R1, R2, R3, R4, R5, R6, R7, R8),
    (L1, L2, L3, L4, R1, R2, R3, R4, R5, R6, R7, R8),
  ] =
    new Combiner[(L1, L2, L3, L4), (R1, R2, R3, R4, R5, R6, R7, R8)] {
      override type Out = (L1, L2, L3, L4, R1, R2, R3, R4, R5, R6, R7, R8)

      override def combine(l: (L1, L2, L3, L4), r: (R1, R2, R3, R4, R5, R6, R7, R8)): Out =
        (l._1, l._2, l._3, l._4, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8)

      override def separate(out: Out): ((L1, L2, L3, L4), (R1, R2, R3, R4, R5, R6, R7, R8)) =
        ((out._1, out._2, out._3, out._4), (out._5, out._6, out._7, out._8, out._9, out._10, out._11, out._12))
    }

  implicit def combine5L8R[L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6, R7, R8]: Combiner.WithOut[
    (L1, L2, L3, L4, L5),
    (R1, R2, R3, R4, R5, R6, R7, R8),
    (L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6, R7, R8),
  ] =
    new Combiner[(L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5, R6, R7, R8)] {
      override type Out = (L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6, R7, R8)

      override def combine(l: (L1, L2, L3, L4, L5), r: (R1, R2, R3, R4, R5, R6, R7, R8)): Out =
        (l._1, l._2, l._3, l._4, l._5, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8)

      override def separate(out: Out): ((L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5, R6, R7, R8)) =
        ((out._1, out._2, out._3, out._4, out._5), (out._6, out._7, out._8, out._9, out._10, out._11, out._12, out._13))
    }

  implicit def combine6L8R[L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6, R7, R8]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6),
    (R1, R2, R3, R4, R5, R6, R7, R8),
    (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6, R7, R8),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5, R6, R7, R8)] {
      override type Out = (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6, R7, R8)

      override def combine(l: (L1, L2, L3, L4, L5, L6), r: (R1, R2, R3, R4, R5, R6, R7, R8)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5, R6, R7, R8)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6),
          (out._7, out._8, out._9, out._10, out._11, out._12, out._13, out._14),
        )
    }

  implicit def combine7L8R[L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6, R7, R8]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7),
    (R1, R2, R3, R4, R5, R6, R7, R8),
    (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6, R7, R8),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4, R5, R6, R7, R8)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6, R7, R8)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7), r: (R1, R2, R3, R4, R5, R6, R7, R8)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4, R5, R6, R7, R8)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7),
          (out._8, out._9, out._10, out._11, out._12, out._13, out._14, out._15),
        )
    }

  implicit def combine8L8R[L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6, R7, R8]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8),
    (R1, R2, R3, R4, R5, R6, R7, R8),
    (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6, R7, R8),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4, R5, R6, R7, R8)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6, R7, R8)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8), r: (R1, R2, R3, R4, R5, R6, R7, R8)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4, R5, R6, R7, R8)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8),
          (out._9, out._10, out._11, out._12, out._13, out._14, out._15, out._16),
        )
    }

  implicit def combine9L8R[L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6, R7, R8]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8, L9),
    (R1, R2, R3, R4, R5, R6, R7, R8),
    (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6, R7, R8),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4, R5, R6, R7, R8)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6, R7, R8)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8, L9), r: (R1, R2, R3, R4, R5, R6, R7, R8)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4, R5, R6, R7, R8)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9),
          (out._10, out._11, out._12, out._13, out._14, out._15, out._16, out._17),
        )
    }

  implicit def combine2L9R[L1, L2, R1, R2, R3, R4, R5, R6, R7, R8, R9]
    : Combiner.WithOut[(L1, L2), (R1, R2, R3, R4, R5, R6, R7, R8, R9), (L1, L2, R1, R2, R3, R4, R5, R6, R7, R8, R9)] =
    new Combiner[(L1, L2), (R1, R2, R3, R4, R5, R6, R7, R8, R9)] {
      override type Out = (L1, L2, R1, R2, R3, R4, R5, R6, R7, R8, R9)

      override def combine(l: (L1, L2), r: (R1, R2, R3, R4, R5, R6, R7, R8, R9)): Out =
        (l._1, l._2, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9)

      override def separate(out: Out): ((L1, L2), (R1, R2, R3, R4, R5, R6, R7, R8, R9)) =
        ((out._1, out._2), (out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11))
    }

  implicit def combine3L9R[L1, L2, L3, R1, R2, R3, R4, R5, R6, R7, R8, R9]: Combiner.WithOut[
    (L1, L2, L3),
    (R1, R2, R3, R4, R5, R6, R7, R8, R9),
    (L1, L2, L3, R1, R2, R3, R4, R5, R6, R7, R8, R9),
  ] =
    new Combiner[(L1, L2, L3), (R1, R2, R3, R4, R5, R6, R7, R8, R9)] {
      override type Out = (L1, L2, L3, R1, R2, R3, R4, R5, R6, R7, R8, R9)

      override def combine(l: (L1, L2, L3), r: (R1, R2, R3, R4, R5, R6, R7, R8, R9)): Out =
        (l._1, l._2, l._3, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9)

      override def separate(out: Out): ((L1, L2, L3), (R1, R2, R3, R4, R5, R6, R7, R8, R9)) =
        ((out._1, out._2, out._3), (out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11, out._12))
    }

  implicit def combine4L9R[L1, L2, L3, L4, R1, R2, R3, R4, R5, R6, R7, R8, R9]: Combiner.WithOut[
    (L1, L2, L3, L4),
    (R1, R2, R3, R4, R5, R6, R7, R8, R9),
    (L1, L2, L3, L4, R1, R2, R3, R4, R5, R6, R7, R8, R9),
  ] =
    new Combiner[(L1, L2, L3, L4), (R1, R2, R3, R4, R5, R6, R7, R8, R9)] {
      override type Out = (L1, L2, L3, L4, R1, R2, R3, R4, R5, R6, R7, R8, R9)

      override def combine(l: (L1, L2, L3, L4), r: (R1, R2, R3, R4, R5, R6, R7, R8, R9)): Out =
        (l._1, l._2, l._3, l._4, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9)

      override def separate(out: Out): ((L1, L2, L3, L4), (R1, R2, R3, R4, R5, R6, R7, R8, R9)) =
        ((out._1, out._2, out._3, out._4), (out._5, out._6, out._7, out._8, out._9, out._10, out._11, out._12, out._13))
    }

  implicit def combine5L9R[L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6, R7, R8, R9]: Combiner.WithOut[
    (L1, L2, L3, L4, L5),
    (R1, R2, R3, R4, R5, R6, R7, R8, R9),
    (L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6, R7, R8, R9),
  ] =
    new Combiner[(L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5, R6, R7, R8, R9)] {
      override type Out = (L1, L2, L3, L4, L5, R1, R2, R3, R4, R5, R6, R7, R8, R9)

      override def combine(l: (L1, L2, L3, L4, L5), r: (R1, R2, R3, R4, R5, R6, R7, R8, R9)): Out =
        (l._1, l._2, l._3, l._4, l._5, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9)

      override def separate(out: Out): ((L1, L2, L3, L4, L5), (R1, R2, R3, R4, R5, R6, R7, R8, R9)) =
        (
          (out._1, out._2, out._3, out._4, out._5),
          (out._6, out._7, out._8, out._9, out._10, out._11, out._12, out._13, out._14),
        )
    }

  implicit def combine6L9R[L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6, R7, R8, R9]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6),
    (R1, R2, R3, R4, R5, R6, R7, R8, R9),
    (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6, R7, R8, R9),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5, R6, R7, R8, R9)] {
      override type Out = (L1, L2, L3, L4, L5, L6, R1, R2, R3, R4, R5, R6, R7, R8, R9)

      override def combine(l: (L1, L2, L3, L4, L5, L6), r: (R1, R2, R3, R4, R5, R6, R7, R8, R9)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6), (R1, R2, R3, R4, R5, R6, R7, R8, R9)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6),
          (out._7, out._8, out._9, out._10, out._11, out._12, out._13, out._14, out._15),
        )
    }

  implicit def combine7L9R[L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6, R7, R8, R9]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7),
    (R1, R2, R3, R4, R5, R6, R7, R8, R9),
    (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6, R7, R8, R9),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4, R5, R6, R7, R8, R9)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, R1, R2, R3, R4, R5, R6, R7, R8, R9)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7), r: (R1, R2, R3, R4, R5, R6, R7, R8, R9)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7), (R1, R2, R3, R4, R5, R6, R7, R8, R9)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7),
          (out._8, out._9, out._10, out._11, out._12, out._13, out._14, out._15, out._16),
        )
    }

  implicit def combine8L9R[L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6, R7, R8, R9]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8),
    (R1, R2, R3, R4, R5, R6, R7, R8, R9),
    (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6, R7, R8, R9),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4, R5, R6, R7, R8, R9)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, R1, R2, R3, R4, R5, R6, R7, R8, R9)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8), r: (R1, R2, R3, R4, R5, R6, R7, R8, R9)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8), (R1, R2, R3, R4, R5, R6, R7, R8, R9)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8),
          (out._9, out._10, out._11, out._12, out._13, out._14, out._15, out._16, out._17),
        )
    }

  implicit def combine9L9R[L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6, R7, R8, R9]: Combiner.WithOut[
    (L1, L2, L3, L4, L5, L6, L7, L8, L9),
    (R1, R2, R3, R4, R5, R6, R7, R8, R9),
    (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6, R7, R8, R9),
  ] =
    new Combiner[(L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4, R5, R6, R7, R8, R9)] {
      override type Out = (L1, L2, L3, L4, L5, L6, L7, L8, L9, R1, R2, R3, R4, R5, R6, R7, R8, R9)

      override def combine(l: (L1, L2, L3, L4, L5, L6, L7, L8, L9), r: (R1, R2, R3, R4, R5, R6, R7, R8, R9)): Out =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9)

      override def separate(out: Out): ((L1, L2, L3, L4, L5, L6, L7, L8, L9), (R1, R2, R3, R4, R5, R6, R7, R8, R9)) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9),
          (out._10, out._11, out._12, out._13, out._14, out._15, out._16, out._17, out._18),
        )
    }

}

trait CombinerLowPriority3 extends CombinerLowPriority4 {
  // Low priority
  implicit def combine1L2R[L1, R1, R2]: Combiner.WithOut[L1, (R1, R2), (L1, R1, R2)] = new Combiner[L1, (R1, R2)] {
    override type Out = (L1, R1, R2)

    override def combine(l: L1, r: (R1, R2)): Out = (l, r._1, r._2)

    override def separate(out: Out): (L1, (R1, R2)) = (out._1, (out._2, out._3))
  }

  implicit def combine1L3R[L1, R1, R2, R3]: Combiner.WithOut[L1, (R1, R2, R3), (L1, R1, R2, R3)] =
    new Combiner[L1, (R1, R2, R3)] {
      override type Out = (L1, R1, R2, R3)

      override def combine(l: L1, r: (R1, R2, R3)): Out = (l, r._1, r._2, r._3)

      override def separate(out: Out): (L1, (R1, R2, R3)) = (out._1, (out._2, out._3, out._4))
    }

  implicit def combine1L4R[L1, R1, R2, R3, R4]: Combiner.WithOut[L1, (R1, R2, R3, R4), (L1, R1, R2, R3, R4)] =
    new Combiner[L1, (R1, R2, R3, R4)] {
      override type Out = (L1, R1, R2, R3, R4)

      override def combine(l: L1, r: (R1, R2, R3, R4)): Out = (l, r._1, r._2, r._3, r._4)

      override def separate(out: Out): (L1, (R1, R2, R3, R4)) = (out._1, (out._2, out._3, out._4, out._5))
    }

  implicit def combine1L5R[L1, R1, R2, R3, R4, R5]
    : Combiner.WithOut[L1, (R1, R2, R3, R4, R5), (L1, R1, R2, R3, R4, R5)] =
    new Combiner[L1, (R1, R2, R3, R4, R5)] {
      override type Out = (L1, R1, R2, R3, R4, R5)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5)): Out = (l, r._1, r._2, r._3, r._4, r._5)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5)) = (out._1, (out._2, out._3, out._4, out._5, out._6))
    }

  implicit def combine1L6R[L1, R1, R2, R3, R4, R5, R6]
    : Combiner.WithOut[L1, (R1, R2, R3, R4, R5, R6), (L1, R1, R2, R3, R4, R5, R6)] =
    new Combiner[L1, (R1, R2, R3, R4, R5, R6)] {
      override type Out = (L1, R1, R2, R3, R4, R5, R6)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5, R6)): Out = (l, r._1, r._2, r._3, r._4, r._5, r._6)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5, R6)) =
        (out._1, (out._2, out._3, out._4, out._5, out._6, out._7))
    }

  implicit def combine1L7R[L1, R1, R2, R3, R4, R5, R6, R7]
    : Combiner.WithOut[L1, (R1, R2, R3, R4, R5, R6, R7), (L1, R1, R2, R3, R4, R5, R6, R7)] =
    new Combiner[L1, (R1, R2, R3, R4, R5, R6, R7)] {
      override type Out = (L1, R1, R2, R3, R4, R5, R6, R7)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5, R6, R7)): Out = (l, r._1, r._2, r._3, r._4, r._5, r._6, r._7)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5, R6, R7)) =
        (out._1, (out._2, out._3, out._4, out._5, out._6, out._7, out._8))
    }

  implicit def combine1L8R[L1, R1, R2, R3, R4, R5, R6, R7, R8]
    : Combiner.WithOut[L1, (R1, R2, R3, R4, R5, R6, R7, R8), (L1, R1, R2, R3, R4, R5, R6, R7, R8)] =
    new Combiner[L1, (R1, R2, R3, R4, R5, R6, R7, R8)] {
      override type Out = (L1, R1, R2, R3, R4, R5, R6, R7, R8)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5, R6, R7, R8)): Out =
        (l, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5, R6, R7, R8)) =
        (out._1, (out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9))
    }

  implicit def combine1L9R[L1, R1, R2, R3, R4, R5, R6, R7, R8, R9]
    : Combiner.WithOut[L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9), (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9)] =
    new Combiner[L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9)] {
      override type Out = (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5, R6, R7, R8, R9)): Out =
        (l, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9)) =
        (out._1, (out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10))
    }

  implicit def combine1L10R[L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10]: Combiner.WithOut[
    L1,
    (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10),
    (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10),
  ] =
    new Combiner[L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10)] {
      override type Out = (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10)): Out =
        (l, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9, r._10)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10)) =
        (out._1, (out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11))
    }

  implicit def combine1L11R[L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11]: Combiner.WithOut[
    L1,
    (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11),
    (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11),
  ] =
    new Combiner[L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11)] {
      override type Out = (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11)): Out =
        (l, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11)) =
        (out._1, (out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11, out._12))
    }

  implicit def combine1L12R[L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12]: Combiner.WithOut[
    L1,
    (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12),
    (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12),
  ] =
    new Combiner[L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12)] {
      override type Out = (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12)): Out =
        (l, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11, r._12)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12)) =
        (out._1, (out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11, out._12, out._13))
    }

  implicit def combine1L13R[L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13]: Combiner.WithOut[
    L1,
    (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13),
    (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13),
  ] =
    new Combiner[L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13)] {
      override type Out = (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13)): Out =
        (l, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11, r._12, r._13)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13)) =
        (
          out._1,
          (out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11, out._12, out._13, out._14),
        )
    }

  implicit def combine1L14R[L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14]: Combiner.WithOut[
    L1,
    (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14),
    (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14),
  ] =
    new Combiner[L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14)] {
      override type Out = (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14)): Out =
        (l, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11, r._12, r._13, r._14)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14)) =
        (
          out._1,
          (
            out._2,
            out._3,
            out._4,
            out._5,
            out._6,
            out._7,
            out._8,
            out._9,
            out._10,
            out._11,
            out._12,
            out._13,
            out._14,
            out._15,
          ),
        )
    }

  implicit def combine1L15R[L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14, R15]: Combiner.WithOut[
    L1,
    (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14, R15),
    (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14, R15),
  ] =
    new Combiner[L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14, R15)] {
      override type Out = (L1, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14, R15)

      override def combine(l: L1, r: (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14, R15)): Out =
        (l, r._1, r._2, r._3, r._4, r._5, r._6, r._7, r._8, r._9, r._10, r._11, r._12, r._13, r._14, r._15)

      override def separate(out: Out): (L1, (R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13, R14, R15)) =
        (
          out._1,
          (
            out._2,
            out._3,
            out._4,
            out._5,
            out._6,
            out._7,
            out._8,
            out._9,
            out._10,
            out._11,
            out._12,
            out._13,
            out._14,
            out._15,
            out._16,
          ),
        )
    }
}

trait CombinerLowPriority4 extends CombinerLowPriority5 {
  implicit def combine34[A, B, C]: Combiner.WithOut[(A, B), C, (A, B, C)] =
    new Combiner[(A, B), C] {
      type Out = (A, B, C)

      def combine(l: (A, B), r: C): (A, B, C) = (l._1, l._2, r)

      def separate(out: (A, B, C)): ((A, B), C) =
        ((out._1, out._2), out._3)
    }
}

trait CombinerLowPriority5 extends CombinerLowPriority6 {

  // (A, B, C) + D -> (A, B, C, D)
  // (A, B, C) + D -> (A, B, C, D)
  implicit def combine3[A, B, C, D]: Combiner.WithOut[(A, B, C), D, (A, B, C, D)] =
    new Combiner[(A, B, C), D] {
      type Out = (A, B, C, D)

      def combine(l: (A, B, C), r: D): (A, B, C, D) = (l._1, l._2, l._3, r)

      def separate(out: (A, B, C, D)): ((A, B, C), D) =
        ((out._1, out._2, out._3), out._4)
    }

  // (A, B, C, D) + E -> (A, B, C, D, E)
  implicit def combine4[A, B, C, D, E]: Combiner.WithOut[(A, B, C, D), E, (A, B, C, D, E)] =
    new Combiner[(A, B, C, D), E] {
      type Out = (A, B, C, D, E)

      def combine(l: (A, B, C, D), r: E): (A, B, C, D, E) = (l._1, l._2, l._3, l._4, r)

      def separate(out: (A, B, C, D, E)): ((A, B, C, D), E) =
        ((out._1, out._2, out._3, out._4), out._5)
    }

  // (A, B, C, D, E) + F -> (A, B, C, D, E, F)
  implicit def combine5[A, B, C, D, E, F]: Combiner.WithOut[(A, B, C, D, E), F, (A, B, C, D, E, F)] =
    new Combiner[(A, B, C, D, E), F] {
      type Out = (A, B, C, D, E, F)

      def combine(l: (A, B, C, D, E), r: F): (A, B, C, D, E, F) = (l._1, l._2, l._3, l._4, l._5, r)

      def separate(out: (A, B, C, D, E, F)): ((A, B, C, D, E), F) =
        ((out._1, out._2, out._3, out._4, out._5), out._6)
    }

  // (A, B, C, D, E, F) + G -> (A, B, C, D, E, F, G)
  implicit def combine6[A, B, C, D, E, F, G]: Combiner.WithOut[(A, B, C, D, E, F), G, (A, B, C, D, E, F, G)] =
    new Combiner[(A, B, C, D, E, F), G] {
      type Out = (A, B, C, D, E, F, G)

      def combine(l: (A, B, C, D, E, F), r: G): (A, B, C, D, E, F, G) = (l._1, l._2, l._3, l._4, l._5, l._6, r)

      def separate(out: (A, B, C, D, E, F, G)): ((A, B, C, D, E, F), G) =
        ((out._1, out._2, out._3, out._4, out._5, out._6), out._7)
    }

  // (A, B, C, D, E, F, G) + H -> (A, B, C, D, E, F, G, H)
  implicit def combine7[A, B, C, D, E, F, G, H]: Combiner.WithOut[(A, B, C, D, E, F, G), H, (A, B, C, D, E, F, G, H)] =
    new Combiner[(A, B, C, D, E, F, G), H] {
      type Out = (A, B, C, D, E, F, G, H)

      def combine(l: (A, B, C, D, E, F, G), r: H): (A, B, C, D, E, F, G, H) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, r)

      def separate(out: (A, B, C, D, E, F, G, H)): ((A, B, C, D, E, F, G), H) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7), out._8)
    }

  // (A, B, C, D, E, F, G, H) + I -> (A, B, C, D, E, F, G, H, I)
  implicit def combine8[A, B, C, D, E, F, G, H, I]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H), I, (A, B, C, D, E, F, G, H, I)] =
    new Combiner[(A, B, C, D, E, F, G, H), I] {
      type Out = (A, B, C, D, E, F, G, H, I)

      def combine(l: (A, B, C, D, E, F, G, H), r: I): (A, B, C, D, E, F, G, H, I) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, r)

      def separate(out: (A, B, C, D, E, F, G, H, I)): ((A, B, C, D, E, F, G, H), I) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8), out._9)
    }

  // (A, B, C, D, E, F, G, H, I) + J -> (A, B, C, D, E, F, G, H, I, J)
  implicit def combine9[A, B, C, D, E, F, G, H, I, J]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I), J, (A, B, C, D, E, F, G, H, I, J)] =
    new Combiner[(A, B, C, D, E, F, G, H, I), J] {
      type Out = (A, B, C, D, E, F, G, H, I, J)

      def combine(l: (A, B, C, D, E, F, G, H, I), r: J): (A, B, C, D, E, F, G, H, I, J) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, r)

      def separate(out: (A, B, C, D, E, F, G, H, I, J)): ((A, B, C, D, E, F, G, H, I), J) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9), out._10)
    }

  // (A, B, C, D, E, F, G, H, I, J) + K -> (A, B, C, D, E, F, G, H, I, J, K)
  implicit def combine10[A, B, C, D, E, F, G, H, I, J, K]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J), K, (A, B, C, D, E, F, G, H, I, J, K)] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J), K] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K)

      def combine(l: (A, B, C, D, E, F, G, H, I, J), r: K): (A, B, C, D, E, F, G, H, I, J, K) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, r)

      def separate(out: (A, B, C, D, E, F, G, H, I, J, K)): ((A, B, C, D, E, F, G, H, I, J), K) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10), out._11)
    }

  // (A, B, C, D, E, F, G, H, I, J, K) + L -> (A, B, C, D, E, F, G, H, I, J, K, L)
  implicit def combine11[A, B, C, D, E, F, G, H, I, J, K, L]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K), L, (A, B, C, D, E, F, G, H, I, J, K, L)] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K), L] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L)

      def combine(l: (A, B, C, D, E, F, G, H, I, J, K), r: L): (A, B, C, D, E, F, G, H, I, J, K, L) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, r)

      def separate(out: (A, B, C, D, E, F, G, H, I, J, K, L)): ((A, B, C, D, E, F, G, H, I, J, K), L) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11), out._12)
    }

  // (A, B, C, D, E, F, G, H, I, J, K, L) + M -> (A, B, C, D, E, F, G, H, I, J, K, L, M)
  implicit def combine12[A, B, C, D, E, F, G, H, I, J, K, L, M]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L), M, (A, B, C, D, E, F, G, H, I, J, K, L, M)] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L), M] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M)

      def combine(l: (A, B, C, D, E, F, G, H, I, J, K, L), r: M): (A, B, C, D, E, F, G, H, I, J, K, L, M) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, r)

      def separate(out: (A, B, C, D, E, F, G, H, I, J, K, L, M)): ((A, B, C, D, E, F, G, H, I, J, K, L), M) =
        ((out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11, out._12), out._13)
    }

  // (A, B, C, D, E, F, G, H, I, J, K, L, M) + N -> (A, B, C, D, E, F, G, H, I, J, K, L, M, N)
  implicit def combine13[A, B, C, D, E, F, G, H, I, J, K, L, M, N]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M), N, (A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M), N] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N)

      def combine(l: (A, B, C, D, E, F, G, H, I, J, K, L, M), r: N): (A, B, C, D, E, F, G, H, I, J, K, L, M, N) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, l._13, r)

      def separate(out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N)): ((A, B, C, D, E, F, G, H, I, J, K, L, M), N) =
        (
          (out._1, out._2, out._3, out._4, out._5, out._6, out._7, out._8, out._9, out._10, out._11, out._12, out._13),
          out._14,
        )
    }

  // (A, B, C, D, E, F, G, H, I, J, K, L, M, N) + O -> (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
  implicit def combine14[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]
    : Combiner.WithOut[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), O, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), O] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)

      def combine(
        l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N),
        r: O,
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, l._13, l._14, r)

      def separate(
        out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N), O) =
        (
          (
            out._1,
            out._2,
            out._3,
            out._4,
            out._5,
            out._6,
            out._7,
            out._8,
            out._9,
            out._10,
            out._11,
            out._12,
            out._13,
            out._14,
          ),
          out._15,
        )
    }

  // (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) + P -> (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
  implicit def combine15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]: Combiner.WithOut[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
    P,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
  ] =
    new Combiner[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), P] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)

      def combine(
        l: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
        r: P,
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) =
        (l._1, l._2, l._3, l._4, l._5, l._6, l._7, l._8, l._9, l._10, l._11, l._12, l._13, l._14, l._15, r)

      def separate(
        out: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), P) =
        (
          (
            out._1,
            out._2,
            out._3,
            out._4,
            out._5,
            out._6,
            out._7,
            out._8,
            out._9,
            out._10,
            out._11,
            out._12,
            out._13,
            out._14,
            out._15,
          ),
          out._16,
        )
    }

}

trait CombinerLowPriority6 {
  implicit def combine[A, B]: Combiner.WithOut[A, B, (A, B)] =
    new Combiner[A, B] {
      type Out = (A, B)

      def combine(l: A, r: B): (A, B) = (l, r)

      def separate(out: (A, B)): (A, B) = (out._1, out._2)
    }
}
