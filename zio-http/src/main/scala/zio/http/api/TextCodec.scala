package zio.http.api

import java.util.UUID
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

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
sealed trait TextCodec[A] {
  def decode(value: String): Option[A]

  def encode(value: A): String
}

object TextCodec {
  implicit val boolean: TextCodec[Boolean] = BooleanCodec

  def constant(string: String): TextCodec[Unit] = Constant(string)

  implicit val int: TextCodec[Int] = IntCodec

  implicit val string: TextCodec[String] = StringCodec

  implicit val uuid: TextCodec[UUID] = UUIDCodec

  private val someUnit: Option[Unit] = Some(())

  final case class Constant(string: String) extends TextCodec[Unit] {

    def decode(value: String): Option[Unit] = if (value == string) someUnit else None

    def encode(value: Unit): String = string
  }

  case object StringCodec extends TextCodec[String] {
    def decode(value: String): Option[String] = Some(value)

    def encode(value: String): String = value
  }

  case object IntCodec extends TextCodec[Int] {
    def decode(value: String): Option[Int] =
      try Some(value.toInt)
      catch { case _: NumberFormatException => None }

    def encode(value: Int): String = value.toString
  }

  case object BooleanCodec extends TextCodec[Boolean] {
    def decode(value: String): Option[Boolean] =
      value match {
        case "true" | "on" | "yes" | "1"  => Some(true)
        case "false" | "off" | "no" | "0" => Some(false)
        case _                            => None
      }

    def encode(value: Boolean): String = value.toString
  }

  case object UUIDCodec extends TextCodec[UUID] {
    def decode(input: String): Option[UUID] =
      try Some(UUID.fromString(input))
      catch {
        case _: IllegalArgumentException => None
      }

    def encode(value: UUID): String = value.toString
  }
}
