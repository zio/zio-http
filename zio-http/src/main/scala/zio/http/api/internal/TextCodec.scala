package zio.http.api.internal

import java.util.UUID
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
import scala.util.control.NonFatal

/**
 * A [[zio.http.api.TextCodec]] defines a codec for a text fragment. The text
 * fragment can be decoded into a value, or the value can be encoded into a text
 * fragment.
 *
 * Unlike parsers, text codecs operate on entire fragments. They do not consume
 * input and leave remainders. Also unlike parsers, text codecs do not fail with
 * error messages, but rather, simply return None if they do not succeed in
 * decoding from a given text fragment. Finally, unlike ordinary parsers, text
 * codecs are fully invertible, and can therefore be used in client generation.
 */
private[api] sealed trait TextCodec[A] extends PartialFunction[String, A] { self =>
  def apply(value: String): A

  // TODO: Implement this using `isDefinedAt` and `apply` but only after all
  // subtypes properly & performantly implement `isDefinedAt`.
  final def decode(value: String): Option[A] =
    if (isDefinedAt(value)) Some(apply(value)) else None

  def describe: String

  def encode(value: A): String

  def isDefinedAt(value: String): Boolean

  private[api] final def erase: TextCodec[Any] = self.asInstanceOf[TextCodec[Any]]
}

private[api] object TextCodec {
  implicit val boolean: TextCodec[Boolean] = BooleanCodec

  def constant(string: String): TextCodec[Unit] = Constant(string)

  implicit val int: TextCodec[Int] = IntCodec

  implicit val string: TextCodec[String] = StringCodec

  implicit val uuid: TextCodec[UUID] = UUIDCodec

  final case class Constant(string: String) extends TextCodec[Unit] {

    def apply(value: String): Unit = if (value == string) () else throw new MatchError(value)

    def describe: String = "the constant string \"" + string + "\""

    def encode(value: Unit): String = string

    def isDefinedAt(value: String): Boolean = value == string

    override def toString(): String = s"TextCodec.constant(${string})"
  }

  case object StringCodec extends TextCodec[String] {
    def apply(value: String): String = value

    def describe: String = "a string"

    def encode(value: String): String = value

    def isDefinedAt(value: String): Boolean = true

    override def toString(): String = "TextCodec.string"
  }

  case object IntCodec extends TextCodec[Int] {
    def apply(value: String): Int = Integer.parseInt(value)

    def describe: String = "an integer"

    def encode(value: Int): String = value.toString

    def isDefinedAt(value: String): Boolean = {
      var i       = 0
      var defined = true
      while (i < value.length) {
        if (!value.charAt(i).isDigit) {
          defined = false
          i = value.length
        }
        i += 1
      }
      defined && i >= 1
    }

    override def toString(): String = "TextCodec.int"
  }

  case object BooleanCodec extends TextCodec[Boolean] {
    def apply(value: String): Boolean =
      value match {
        case "true" | "on" | "yes" | "1"  => true
        case "false" | "off" | "no" | "0" => false
        case _                            => throw new MatchError(value)
      }

    def describe: String = "a boolean value"

    def encode(value: Boolean): String = value.toString

    // TODO: Make faster by hand-writing validation:
    def isDefinedAt(value: String): Boolean = value == "1" || value == "0" || value == "true" || value == "false" ||
      value == "no" || value == "off" || value == "yes" || value == "on"

    override def toString(): String = "TextCodec.boolean"
  }

  case object UUIDCodec extends TextCodec[UUID] {
    def apply(input: String): UUID = UUID.fromString(input)

    def describe: String = "a UUID"

    def encode(value: UUID): String = value.toString

    // TODO: Make faster by hand-writing validation:
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

    override def toString(): String = "TextCodec.uuid"
  }

}
