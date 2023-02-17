package zio.http.codec

import java.util.UUID

import zio.http.codec.HttpCodec
private[codec] trait PathCodecs {
  def literal(string: String): PathCodec[Unit] =
    HttpCodec.Path(TextCodec.constant(string), None)

  def int(name: String): PathCodec[Int] =
    HttpCodec.Path(TextCodec.int, Some(name))

  def string(name: String): PathCodec[String] =
    HttpCodec.Path(TextCodec.string, Some(name))

  def uuid(name: String): PathCodec[UUID] =
    HttpCodec.Path(TextCodec.uuid, Some(name))
}
