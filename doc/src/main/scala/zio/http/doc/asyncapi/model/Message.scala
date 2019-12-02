package zio.http.doc.asyncapi.model

final case class Message[T](
  headers: Map[SchemaProperty, String],
  payload: T,
  correlationId: CorrelationId,
  schemaFormat: String,
  contentType: String,
  name: String,
  title: String,
  summary: String,
  description: String,
  tags: List[Tag],
  externalDocs: ExternalDocumentation,
  bindings: Map[String, MessageBinding],
  examples: List[Map[String, Any]],
  traits: List[Trait]
)
