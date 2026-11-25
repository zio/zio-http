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

import java.util.UUID

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A [[zio.http.codec.TextCodec]] defines a codec for a text fragment. The text
 * fragment can be decoded into a value, or the value can be encoded into a text
 * fragment.
 *
 * Unlike parsers, text codecs operate on entire fragments. They do not consume
 * input and leave remainders. Also unlike parsers, `decode` does not fail with
 * error messages, but rather returns `None` when `isDefinedAt` is `false` for
 * the text fragment. Finally, unlike ordinary parsers, text codecs can encode
 * values back to text, and can therefore be used in client generation.
 *
 * {{{
 * import zio.http.codec._
 *
 * val page: Option[Int]   = TextCodec.int.decode("42")   // Some(42)
 * val bad: Option[Int]    = TextCodec.int.decode("4x2")  // None
 * val text: String        = TextCodec.int.encode(42)     // "42"
 * val flag: Option[Boolean] = TextCodec.boolean.decode("yes") // Some(true)
 * }}}
 */
sealed trait TextCodec[A] extends PartialFunction[String, A] { self =>

  /**
   * Decodes `value`. It throws an exception when `isDefinedAt(value)` is
   * `false`.
   */
  def apply(value: String): A

  /**
   * Decodes `value` when `isDefinedAt(value)` is `true`, and returns `None`
   * otherwise.
   */
  final def decode(value: String): Option[A] =
    if (isDefinedAt(value)) Some(apply(value)) else None

  /**
   * Returns a short description of the accepted text, such as `"an integer"`.
   */
  def describe: String

  /** Encodes `value` as text. */
  def encode(value: A): String

  /** Returns `true` when this codec accepts `value`. */
  def isDefinedAt(value: String): Boolean

  private[http] final def erase: TextCodec[Any] = self.asInstanceOf[TextCodec[Any]]
}

/** The predefined text codecs. */
object TextCodec {

  /** The codec of booleans: see [[BooleanCodec]]. */
  implicit val boolean: TextCodec[Boolean] = BooleanCodec

  /**
   * Returns a codec that accepts only `string`, case-sensitively, and decodes
   * it to `()`.
   */
  def constant(string: String): TextCodec[Unit] = Constant(string)

  /** The codec of `Int` values: see [[IntCodec]]. */
  implicit val int: TextCodec[Int] = IntCodec

  /** The codec of `Long` values: see [[LongCodec]]. */
  implicit val long: TextCodec[Long] = LongCodec

  /** The codec that accepts any text and returns it unchanged. */
  implicit val string: TextCodec[String] = StringCodec

  /** The codec of `UUID` values: see [[UUIDCodec]]. */
  implicit val uuid: TextCodec[UUID] = UUIDCodec

  /**
   * A codec that accepts only `string`, case-sensitively, and decodes it to
   * `()`.
   */
  final case class Constant(string: String) extends TextCodec[Unit] {

    /**
     * Returns `()` when `value` equals `string`, and throws a
     * `scala.MatchError` otherwise.
     */
    def apply(value: String): Unit = if (value == string) () else throw new MatchError(value)

    /**
     * Returns a description such as `the constant string "v1"` for
     * `constant("v1")`.
     */
    def describe: String = "the constant string \"" + string + "\""

    /** Returns `string`. */
    def encode(value: Unit): String = string

    /** Returns `true` when `value` equals `string`. */
    def isDefinedAt(value: String): Boolean = value == string

    override def toString: String = s"TextCodec.constant(${string})"
  }

  /** The codec that accepts any text and returns it unchanged. */
  case object StringCodec extends TextCodec[String] {

    /** Returns `value` unchanged. */
    def apply(value: String): String = value

    /** Returns `"a string"`. */
    def describe: String = "a string"

    /** Returns `value` unchanged. */
    def encode(value: String): String = value

    /** Returns `true` for every value. */
    def isDefinedAt(value: String): Boolean = true

    override def toString: String = "TextCodec.string"
  }

  /**
   * The codec of `Int` values in decimal, with an optional leading `-` and no
   * `+` sign. `isDefinedAt` checks only the characters, so text with too many
   * digits for an `Int`, such as `"99999999999"`, passes it, and `decode` then
   * throws a `java.lang.NumberFormatException`.
   */
  case object IntCodec extends TextCodec[Int] {

    /**
     * Parses `value` with `Integer.parseInt`, which throws a
     * `java.lang.NumberFormatException` for invalid text.
     */
    def apply(value: String): Int = Integer.parseInt(value)

    /** Returns `"an integer"`. */
    def describe: String = "an integer"

    /** Returns `value` in decimal. */
    def encode(value: Int): String = value.toString

    /**
     * Returns `true` when `value` is an optional `-` followed by one or more
     * digits.
     */
    def isDefinedAt(value: String): Boolean = {
      var i       = 0
      var defined = true
      if (value.length > 1 && value.charAt(0) == '-') i += 1
      while (i < value.length) {
        if (!value.charAt(i).isDigit) {
          defined = false
          i = value.length
        }
        i += 1
      }
      defined && i >= 1
    }

    override def toString: String = "TextCodec.int"
  }

  /**
   * The codec of `Long` values in decimal, with an optional leading `-` and no
   * `+` sign. `isDefinedAt` checks only the characters, so text with too many
   * digits for a `Long` passes it, and `decode` then throws a
   * `java.lang.NumberFormatException`.
   */
  case object LongCodec extends TextCodec[Long] {

    /**
     * Parses `value` with `java.lang.Long.parseLong`, which throws a
     * `java.lang.NumberFormatException` for invalid text.
     */
    def apply(value: String): Long = java.lang.Long.parseLong(value)

    /** Returns `"a long integer"`. */
    def describe: String = "a long integer"

    /** Returns `value` in decimal. */
    def encode(value: Long): String = value.toString

    /**
     * Returns `true` when `value` is an optional `-` followed by one or more
     * digits.
     */
    def isDefinedAt(value: String): Boolean = {
      var i       = 0
      var defined = true
      if (value.length > 1 && value.charAt(0) == '-') i += 1
      while (i < value.length) {
        if (!value.charAt(i).isDigit) {
          defined = false
          i = value.length
        }
        i += 1
      }
      defined && i >= 1
    }

    override def toString: String = "TextCodec.long"
  }

  /**
   * The codec of booleans. It decodes `true`, `on`, `yes` and `1` to `true`,
   * and `false`, `off`, `no` and `0` to `false`, ignoring case. It encodes to
   * `"true"` or `"false"`.
   */
  case object BooleanCodec extends TextCodec[Boolean] {

    /**
     * Decodes `value`, and throws a `scala.MatchError` when it is not one of
     * the accepted words.
     */
    def apply(value: String): Boolean =
      value.toLowerCase match {
        case "true" | "on" | "yes" | "1"  => true
        case "false" | "off" | "no" | "0" => false
        case _                            => throw new MatchError(value)
      }

    /** Returns `"a boolean value"`. */
    def describe: String = "a boolean value"

    /** Returns `"true"` or `"false"`. */
    def encode(value: Boolean): String = value.toString

    /**
     * Returns `true` when `value` is one of the accepted words, ignoring case.
     */
    def isDefinedAt(value: String): Boolean = {
      val valueLower = value.toLowerCase
      valueLower == "1" || valueLower == "0" || valueLower == "true" || valueLower == "false" ||
      valueLower == "no" || valueLower == "off" || valueLower == "yes" || valueLower == "on"
    }

    override def toString: String = "TextCodec.boolean"
  }

  /**
   * The codec of UUIDs in the form `123e4567-e89b-12d3-a456-426614174000`, with
   * hex digits in either case. `isDefinedAt` also accepts 36 hex digits without
   * dashes, and `decode` then throws a `java.lang.IllegalArgumentException`.
   */
  case object UUIDCodec extends TextCodec[UUID] {

    /**
     * Parses `input` with `UUID.fromString`, which throws a
     * `java.lang.IllegalArgumentException` for invalid text.
     */
    def apply(input: String): UUID = UUID.fromString(input)

    /** Returns `"a UUID"`. */
    def describe: String = "a UUID"

    /** Returns `value` in lower-case hex with dashes. */
    def encode(value: UUID): String = value.toString

    /**
     * Returns `true` for UUIDs in the 8-4-4-4-12 form, and also for some other
     * 36-character strings of hex digits and dashes, such as 36 hex digits
     * without dashes.
     */
    def isDefinedAt(value: String): Boolean = {
      var i       = 0
      var defined = true
      var group   = 0
      var count   = 0
      while (i < value.length) {
        val char = value.charAt(i)
        if ((char >= 48 && char <= 57) || (char >= 65 && char <= 70) || (char >= 97 && char <= 102))
          count += 1
        else if (char == 45) {
          if (
            group > 4 || (group == 0 && count != 8) || ((group == 1 || group == 2 || group == 3) && count != 4) || (group == 4 && count != 12)
          ) {
            defined = false
            i = value.length
          }
          count = 0
          group += 1
        } else {
          defined = false
          i = value.length
        }
        i += 1
      }
      defined && i == 36
    }

    override def toString: String = "TextCodec.uuid"
  }

}
