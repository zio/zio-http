package zio.http.doc.asyncapi.model

case class Component[T](
  schemas: Option[Map[String, SchemaProperty]],
  messages: Option[Map[String, Message[T]]],
  securitySchemes: Option[Map[String, Security]],
  parameters: Option[Map[String, Parameter]],
  correlationIds: Option[Map[String, CorrelationId]],
  operationTraits: Option[Map[String, OperationTraitObject]],
  messageTraits: Option[Map[String, MessageTrait]],
  serverBindings: Option[Map[String, Map[String, ServerBinding]]],
  channelBindings: Option[Map[String, Map[String, ChannelBinding]]],
  operationBindings: Option[Map[String, Map[String, OperationBinding]]],
  messageBindings: Option[Map[String, Map[String, MessageBinding]]]
)

case class MessageTrait(
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
