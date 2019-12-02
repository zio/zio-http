package zio.http.doc.asyncapi.model

import java.net.URL

/*
  License information for the exposed API
 */
final case class License(
  name: String,
  url: Option[URL]
)
