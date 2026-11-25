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
 * A simple codec is either equal to a given value, or unconstrained within a
 * domain of values. There are no other possibilities.
 */
sealed trait SimpleCodec[Input, Output] extends PartialFunction[Input, Output] {

  /** Returns `true` when `decode` succeeds for `input`. */
  def isDefinedAt(input: Input): Boolean

  /**
   * Decodes `input`, or returns a `Left` with an error message when `input`
   * does not match.
   */
  def decode(input: Input): Either[String, Output]

  /** Encodes `output` back into an input value. */
  def encode(output: Output): Input
}

/** The two kinds of [[SimpleCodec]]: `Specified` and `Unspecified`. */
object SimpleCodec {

  /**
   * A codec that matches only `value`. It decodes `value` to `()` and encodes
   * `()` to `value`. `HttpCodec.status(Status.NotFound)` and
   * `HttpCodec.method(Method.GET)` are built on it.
   */
  final case class Specified[A](value: A) extends SimpleCodec[A, Unit] {

    /**
     * Returns `()` when `input` equals `value`, and throws a `scala.MatchError`
     * otherwise.
     */
    def apply(input: A): Unit =
      if (isDefinedAt(input)) () else throw new MatchError(s"Expected $value but found $input")

    /**
     * Returns `Right(())` when `input` equals `value`, and otherwise a `Left`
     * with a message that names `value` and `input`.
     */
    def decode(input: A): Either[String, Unit] =
      if (isDefinedAt(input)) Right(()) else Left(s"Expected $value but found $input")

    /** Returns `value`. */
    def encode(output: Unit): A = value

    /** Returns `true` when `input` equals `value`. */
    def isDefinedAt(input: A): Boolean = input == value
  }

  /**
   * A codec that accepts any input and returns it unchanged, in both
   * directions. The `HttpCodec.method` codec, which reads the method of a
   * request as a value, is built on it.
   */
  final case class Unspecified[A]() extends SimpleCodec[A, A] {

    /** Returns `input` unchanged. */
    def apply(input: A): A = input

    /** Returns `Right(input)`. */
    def decode(input: A): Either[String, A] =
      Right(input)

    /** Returns `output` unchanged. */
    def encode(output: A): A = output

    /** Returns `true` for every input. */
    def isDefinedAt(input: A): Boolean = true
  }
}
