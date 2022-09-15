package zio.http.api

import java.util.UUID

sealed trait TextCodec[A] {
  def decode(value: String): Option[A]

  def encode(value: A): String
}

object TextCodec {
  val boolean: TextCodec[Boolean] = BooleanCodec

  def constant(string: String): TextCodec[Unit] = Constant(string)

  val int: TextCodec[Int] = IntCodec

  val string: TextCodec[String] = StringCodec

  val uuid: TextCodec[UUID] = UUIDCodec

  final case class Constant(string: String) extends TextCodec[Unit] {
    def decode(value: String): Option[Unit] = Some(())

    def encode(value: Unit): String = string
  }

  final case object StringCodec extends TextCodec[String] {
    def decode(value: String): Option[String] = Some(value)

    def encode(value: String): String = value
  }

  final case object IntCodec extends TextCodec[Int] {
    def decode(value: String): Option[Int] = value.toIntOption

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
