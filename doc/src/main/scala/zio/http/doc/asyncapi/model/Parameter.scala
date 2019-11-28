package zio.http.doc.asyncapi.model

final case class Parameter(
  description: String,
  schema: Map[SchemaProperty, String],
  location: String
)
