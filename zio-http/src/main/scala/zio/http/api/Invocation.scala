package zio.http.api

final case class Invocation[Id, A, B](api: API.WithId[A, B, Id], input: A)
