package zio.http.endpoint

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.Path
import zio.http.codec.TextCodec

final case class EndpointNotFound(message: String, api: Endpoint[_, _, _, _]) extends Exception(message)
