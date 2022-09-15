package zio.http.api

import java.util.UUID

trait TextCodec[A] {
  def decode(value: String): Option[A]

  def encode(value: A): String

  final def transform[B](f: A => B, g: B => A): TextCodec[B] =
    TextCodec(input => decode(input).map(f), b => encode(g(b)))
}
object TextCodec   {
  def apply[A](decode0: String => Option[A], encode0: A => String): TextCodec[A] = new TextCodec[A] {
    def decode(value: String): Option[A] = decode0(value)
    def encode(value: A): String         = encode0(value)
  }

  def constant(string: String): TextCodec[Unit] = TextCodec(_ => Some(()), _ => string)

  val int: TextCodec[Int] = TextCodec[Int](_.toIntOption, _.toString)

  val boolean: TextCodec[Boolean] = TextCodec[Boolean](
    {
      case "true" | "on" | "yes" | "1"  => Some(true)
      case "false" | "off" | "no" | "0" => Some(false)
      case _                            => None
    },
    _.toString,
  )

  val unit: TextCodec[Unit] = TextCodec[Unit](_ => Some(()), _ => "")

  val uuid: TextCodec[UUID] =
    TextCodec[UUID](
      input =>
        try Some(UUID.fromString(input))
        catch {
          case _: IllegalArgumentException => None
        },
      _.toString,
    )
}
