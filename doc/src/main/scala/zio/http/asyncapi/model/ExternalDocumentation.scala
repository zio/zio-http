package zio.http.asyncapi.model

import java.net.URL

case class ExternalDocumentation(
  description: Option[String],
  url: URL
)
