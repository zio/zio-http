package zio.http

import zio.model.{ ContentType, Cookie, Header, StatusCode }

final case class Response[T](
  headers: List[Header],
  status: StatusCode,
  cookies: List[Cookie],
  contentType: ContentType,
  body: T
)
