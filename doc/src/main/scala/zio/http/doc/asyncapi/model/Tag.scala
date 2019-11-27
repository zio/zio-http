package zio.http.doc.asyncapi.model

case class Tag(
  name: String,
  description: Option[String],
  externalDocs: ExternalDocumentation
)
