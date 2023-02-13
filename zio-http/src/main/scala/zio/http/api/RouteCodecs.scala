package zio.http.api

import zio.http.api.internal.TextCodec

import java.util.UUID

private[api] trait RouteCodecs {
  def literal(string: String): RouteCodec[Unit] =
    HttpCodec.Route(TextCodec.constant(string), None)

  def int(name: String): RouteCodec[Int] =
    HttpCodec.Route(TextCodec.int, Some(name))

  def string(name: String): RouteCodec[String] =
    HttpCodec.Route(TextCodec.string, Some(name))

  def uuid(name: String): RouteCodec[UUID] =
    HttpCodec.Route(TextCodec.uuid, Some(name))
}
