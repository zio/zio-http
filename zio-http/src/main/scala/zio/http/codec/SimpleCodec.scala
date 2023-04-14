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
 * A simple codec is either equal to a given value, or unconstrained within a
 * domain of values.
 */
sealed trait SimpleCodec[Input, Output] extends PartialFunction[Input, Output] {
  def isDefinedAt(input: Input): Boolean

  def decode(input: Input): Either[String, Output]

  def encode(output: Output): Input
}
object SimpleCodec {
  final case class Specified[A](value: A) extends SimpleCodec[A, Unit] {
    def apply(input: A): Unit =
      if (isDefinedAt(input)) () else throw new MatchError(s"Expected $value but found $input")

    def decode(input: A): Either[String, Unit] =
      if (isDefinedAt(input)) Right(()) else Left(s"Expected $value but found $input")

    def encode(output: Unit): A = value

    def isDefinedAt(input: A): Boolean = input == value
  }
  final case class Unspecified[A]()       extends SimpleCodec[A, A]    {
    def apply(input: A): A = input

    def decode(input: A): Either[String, A] =
      Right(input)

    def encode(output: A): A = output

    def isDefinedAt(input: A): Boolean = true
  }
}
