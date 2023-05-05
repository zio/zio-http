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

package zio.http.codec.internal

import zio.http.codec.HttpCodec._
import zio.http.codec.{HttpCodec, HttpCodecError}

private[http] object Mechanic {
  type InputsBuilder = Atomized[Array[Any]]

  type Constructor[+A]   = InputsBuilder => A
  type Deconstructor[-A] = A => InputsBuilder

  private def indexed[R, A](api: HttpCodec[R, A]): HttpCodec[R, A] =
    indexedImpl(api, Atomized(0))._1

  private def indexedImpl[R, A](api: HttpCodec[R, A], indices: Atomized[Int]): (HttpCodec[R, A], Atomized[Int]) =
    api.asInstanceOf[HttpCodec[_, _]] match {
      case Combine(left, right, inputCombiner) =>
        val (left2, leftIndices)   = indexedImpl(left, indices)
        val (right2, rightIndices) = indexedImpl(right, leftIndices)
        (Combine(left2, right2, inputCombiner).asInstanceOf[HttpCodec[R, A]], rightIndices)
      case atom: Atom[_, _]                    =>
        (atom.withIndex(indices.get(atom.tag)).asInstanceOf[HttpCodec[R, A]], indices.update(atom.tag)(_ + 1))
      case TransformOrFail(api, f, g)          =>
        val (api2, resultIndices) = indexedImpl(api, indices)
        (TransformOrFail(api2, f, g).asInstanceOf[HttpCodec[R, A]], resultIndices)

      case WithDoc(api, _)      => indexedImpl(api.asInstanceOf[HttpCodec[R, A]], indices)
      case WithExamples(api, _) => indexedImpl(api.asInstanceOf[HttpCodec[R, A]], indices)
      case Empty                => (Empty.asInstanceOf[HttpCodec[R, A]], indices)
      case Halt                 => (Halt.asInstanceOf[HttpCodec[R, A]], indices)
      case Fallback(_, _)       => throw new UnsupportedOperationException("Cannot handle fallback at this level")
    }

  def makeConstructor[R, A](
    api: HttpCodec[R, A],
  ): Constructor[A] =
    makeConstructorLoop(indexed(api))

  def makeDeconstructor[R, A](
    api: HttpCodec[R, A],
  ): Deconstructor[A] = {
    val flattened = AtomizedCodecs.flatten(api)

    val deconstructor = makeDeconstructorLoop(indexed(api))

    (a: A) => {
      val inputsBuilder = flattened.makeInputsBuilder()
      deconstructor(a, inputsBuilder)
      inputsBuilder
    }
  }

  private def makeConstructorLoop[A](
    api: HttpCodec[Nothing, A],
  ): Constructor[A] = {
    def coerce(any: Any): A = any.asInstanceOf[A]

    api match {
      case Combine(left, right, inputCombiner) =>
        val leftThread  = makeConstructorLoop(left)
        val rightThread = makeConstructorLoop(right)

        results => {
          val leftValue  = leftThread(results)
          val rightValue = rightThread(results)
          inputCombiner.combine(leftValue, rightValue)
        }

      case atom: Atom[_, _] =>
        results => coerce(results.get(atom.tag)(atom.index))

      case transform: TransformOrFail[_, _, A] =>
        val threaded = makeConstructorLoop(transform.api)
        results =>
          transform.f(threaded(results)) match {
            case Left(value)  => throw HttpCodecError.CustomError(value)
            case Right(value) => value
          }

      case WithDoc(api, _) => makeConstructorLoop(api)

      case WithExamples(api, _) => makeConstructorLoop(api)

      case Empty =>
        _ => coerce(())

      case Halt => throw HaltException

      case Fallback(_, _) => throw new UnsupportedOperationException("Cannot handle fallback at this level")
    }
  }

  private def makeDeconstructorLoop[R, A](
    api: HttpCodec[R, A],
  ): (A, InputsBuilder) => Unit = {
    api match {
      case Combine(left, right, inputCombiner) =>
        val leftDeconstructor  = makeDeconstructorLoop(left)
        val rightDeconstructor = makeDeconstructorLoop(right)

        (input, inputsBuilder) => {
          val (left, right) = inputCombiner.separate(input)

          leftDeconstructor(left, inputsBuilder)
          rightDeconstructor(right, inputsBuilder)
        }

      case atom: Atom[_, _] =>
        (input, inputsBuilder) =>
          val array = inputsBuilder.get(atom.tag)
          array(atom.index) = input

      case transform: TransformOrFail[_, _, A] =>
        val deconstructor = makeDeconstructorLoop(transform.api)

        (input, inputsBuilder) =>
          deconstructor(
            transform.g(input) match {
              case Left(value)  => throw HttpCodecError.CustomError(value)
              case Right(value) => value
            },
            inputsBuilder,
          )

      case WithDoc(api, _) => makeDeconstructorLoop(api)

      case WithExamples(api, _) => makeDeconstructorLoop(api)

      case Empty => (_, _) => ()

      case Halt => (_, _) => ()

      case Fallback(_, _) => throw new UnsupportedOperationException("Cannot handle fallback at this level")
    }
  }
}
