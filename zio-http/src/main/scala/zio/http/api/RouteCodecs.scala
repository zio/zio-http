package zio.http.api

import java.util.UUID
import zio.http.api.internal.TextCodec
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] trait RouteCodecs {
  def literal(string: String): RouteCodec[Unit] =
    HttpCodec.Route(TextCodec.constant(string))

  val int: RouteCodec[Int] =
    HttpCodec.Route(TextCodec.int)

  val string: RouteCodec[String] =
    HttpCodec.Route(TextCodec.string)

  val uuid: RouteCodec[UUID] =
    HttpCodec.Route(TextCodec.uuid)
}
