package zio.http

import zio.http.model.{ ContentType, Cookie, StatusCode }

final case class Response[T](
  headers: List[Header],
  status: StatusCode,
  cookies: List[Cookie],
  contentType: ContentType,
  body: T
)
