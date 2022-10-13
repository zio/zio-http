package zio.http.api

import java.util.UUID
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] trait RouteInputs {
  def literal(string: String): HttpCodec[CodecType.Route, Unit] =
    In.Route(TextCodec.constant(string))

  val int: HttpCodec[CodecType.Route, Int] =
    In.Route(TextCodec.int)

  val string: HttpCodec[CodecType.Route, String] =
    In.Route(TextCodec.string)

  val uuid: HttpCodec[CodecType.Route, UUID] =
    In.Route(TextCodec.uuid)
}
