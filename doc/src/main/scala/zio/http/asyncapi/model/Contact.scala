package zio.http.asyncapi.model

import java.net.URL

final case class Contact(
  name: String,
  url: URL,
  email: String
)
