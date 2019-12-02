package zio.http.doc.asyncapi.model

/*
  Objects representing different aspects of the API
 */
final case class Component[T](
  schemas: Map[String, SchemaProperty],
  messages: Map[String, Message[T]],
  securitySchemes: Map[String, Security],
  parameters: Map[String, Parameter],
  correlationIds: Map[String, CorrelationId],
  operationTraits: Map[String, OperationTraitObject],
  messageTraits: Map[String, MessageTrait],
  serverBindings: Map[String, Map[String, ServerBinding]],
  channelBindings: Map[String, Map[String, ChannelBinding]],
  operationBindings: Map[String, Map[String, OperationBinding]],
  messageBindings: Map[String, Map[String, MessageBinding]]
)

final case class MessageTrait(
  headers: Map[SchemaProperty, String],
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
  examples: List[Map[String, Any]]
)

final case class OperationTraitObject(
  operationId: String,
  summary: String,
  description: String,
  tags: List[Tag],
  externalDocs: ExternalDocumentation,
  bindings: Map[String, OperationBinding]
)
