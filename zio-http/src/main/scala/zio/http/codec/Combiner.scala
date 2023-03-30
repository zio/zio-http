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
  // (A, B) + C -> (A, B, C)
  implicit def combine2[A, B, C]: Combiner.WithOut[(A, B), C, (A, B, C)] =
    new Combiner[(A, B), C] {
      type Out = (A, B, C)

      def combine(l: (A, B), r: C): (A, B, C) = (l._1, l._2, r)

      def separate(out: (A, B, C)): ((A, B), C) =
        ((out._1, out._2), out._3)
    }
}

trait CombinerLowPriority3 extends CombinerLowPriority4 {

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

}

trait CombinerLowPriority4 {

  implicit def combine[A, B]: Combiner.WithOut[A, B, (A, B)] =
    new Combiner[A, B] {
      type Out = (A, B)

      def combine(l: A, r: B): (A, B) = (l, r)

      def separate(out: (A, B)): (A, B) = (out._1, out._2)
    }
}
