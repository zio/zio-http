package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class Invocation[MI, MO, Id, A, B](api: API.WithId[MI, MO, A, B, Id], input: A)
