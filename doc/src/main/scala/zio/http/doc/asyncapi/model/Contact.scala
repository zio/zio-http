package zio.http.doc.asyncapi.model

import java.net.URL

/*
  Contact information for the exposed API
 */
final case class Contact(
  name: String,
  url: URL,
  email: String
)
