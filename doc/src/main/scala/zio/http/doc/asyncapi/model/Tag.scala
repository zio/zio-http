package zio.http.doc.asyncapi.model

/*
  Metadata used for API documentation control
 */
final case class Tag(
  name: String,
  description: Option[String],
  externalDocs: ExternalDocumentation
)
