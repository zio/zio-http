package zio.http

import zio.http.model.Header

final case class Response[T](
  headers: List[Header],
  status: StatusCode,
  cookies: List[Cookie],
  contentType: String,
  body: T
)
