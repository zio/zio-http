package zio.http.api

import java.util.UUID
import zio.http.api.internal.TextCodec
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] trait PathCodecs {
  def literal(string: String): PathCodec[Unit] =
    HttpCodec.Route(TextCodec.constant(string), None)

  def int(name: String): PathCodec[Int] =
    HttpCodec.Route(TextCodec.int, Some(name))

  def string(name: String): PathCodec[String] =
    HttpCodec.Route(TextCodec.string, Some(name))

  def uuid(name: String): PathCodec[UUID] =
    HttpCodec.Route(TextCodec.uuid, Some(name))
}
