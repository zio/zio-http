package zio.http.api

final case class Invocation[Id, A, B](api: EndpointSpec[A, B], input: A)
