package zio.http.asyncapi.model

case class Tag(
  name: String,
  description: Option[String],
  externalDocs: ExternalDocumentation
)
