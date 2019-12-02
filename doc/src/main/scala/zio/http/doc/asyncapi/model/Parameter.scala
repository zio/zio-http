package zio.http.doc.asyncapi.model

/*
  Represents a parameter used in the channel name
  e.g. query parameters
 */
final case class Parameter(
  description: String,
  schema: Map[SchemaProperty, String],
  location: String
)
