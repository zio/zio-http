package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
final case class Invocation[Id, A, B](api: API.WithId[A, B, Id], input: A)
