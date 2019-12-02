package zio.http.doc.asyncapi.model

import java.net.URL

/*
  External Documentation
 */
final case class ExternalDocumentation(
  description: Option[String],
  url: URL
)
