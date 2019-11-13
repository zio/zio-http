package zio.http.model

final case class Response[T](
  headers: List[Header],
  status: StatusCode,
  cookies: List[Cookie],
  contentType: ContentType,
  body: T
)
