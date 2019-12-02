package zio.http.doc.asyncapi.model

/*
  API metadata
 */
final case class Info(
  title: String,
  version: String,
  description: Option[String],
  termsOfService: Option[String],
  contact: Option[Contact],
  license: Option[License]
)
