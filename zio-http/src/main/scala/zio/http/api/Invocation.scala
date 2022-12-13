package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class Invocation[Id, A, E, B](api: EndpointSpec[A, E, B], input: A)
