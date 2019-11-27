package zio.http.doc.asyncapi.model

import java.net.URL

final case class Contact(
  name: String,
  url: URL,
  email: String
)
