package zio.http.endpoint

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
import zio.http.codec.TextCodec
import zio.http.Path

final case class EndpointNotFound(message: String, api: Endpoint[_, _, _, _]) extends Exception(message)
