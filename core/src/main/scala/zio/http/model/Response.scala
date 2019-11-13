package zio.http.model

import zio.http.Header

final case class Response[T](
  headers: List[Header],
  status: StatusCode,
  cookies: List[Cookie],
  contentType: ContentType,
  body: T
)
