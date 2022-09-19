package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace
final case class Invocation[Id, A, B](api: API.WithId[A, B, Id], input: A)
