package zio.http.asyncapi.model

import java.net.URL

final case class License(
  name: String,
  url: Option[URL]
)
