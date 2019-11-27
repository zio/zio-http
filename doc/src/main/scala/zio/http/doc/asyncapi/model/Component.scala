package zio.http.doc.asyncapi.model

import A.SchemaObject

sealed abstract class A extends Product with Serializable

object A {
  final case class SchemaObject(discriminator: String, externalDocs: ExternalDocumentation, deprecated: Boolean)
      extends A
  final case class ReferenceObject($ref: String) extends A
}

sealed abstract class B extends Product with Serializable

object B {
  final case class MessageObject(
    headers: A,
    payload: Any,
    correlationId: C,
    schemaFormat: String,
    contentType: String,
    name: String,
    title: String,
    summary: String,
    description: String,
    tags: List[Tag],
    externalDocs: ExternalDocumentation,
    bindings: List[MessageBinding],
    examples: List[Map[String, Any]],
    traits: List[MessageTrait]
  ) extends B
  final case class ReferenceObject($ref: String) extends B
}

sealed abstract class C extends Product with Serializable

object C {
  final case class CorrelationIdObject(description: String, location: String) extends C
  final case class ReferenceObject($ref: String)                              extends C
}

sealed abstract class D extends Product with Serializable

object D {
  final case class SecuritySchemeObject(
    `type`: SecuritySchemes,
    description: String,
    name: String,
    in: String,
    scheme: String,
    bearerFormat: String,
    flows: OAuthFlows,
    openIdConnectUrl: String
  ) extends D
  final case class ReferenceObject($ref: String) extends D
}

sealed abstract class E extends Product with Serializable

object E {
  final case class ParameterObject(description: String, schema: SchemaObject, location: String) extends E
  final case class ReferenceObject($ref: String)                                                extends E
}
case class MessageTrait(
  headers: A,
  correlationId: C,
  schemaFormat: String,
  contentType: String,
  name: String,
  title: String,
  summary: String,
  description: String,
  tag: List[Tag],
  externalDocs: ExternalDocumentation,
  bindings: Map[String, MessageBinding],
  examples: List[Map[String, Any]]
)

sealed abstract class F extends Product with Serializable

object F {
  final case class OperationTraitObject(
    operationId: String,
    summary: String,
    description: String,
    tags: List[Tag],
    externalDocs: ExternalDocumentation,
    bindings: OperationBinding
  ) extends F
  final case class ReferenceObject($ref: String) extends E
}

case class Component(
  schemas: Map[String, A],
  messages: Map[String, B],
  securitySchemes: Map[String, D],
  parameters: Map[String, E],
  correlationIds: Map[String, C],
  operationTraits: Map[String, F],
  messageTraits: Map[String, MessageTrait],
  serverBindings: Map[String, ServerBinding],
  channelBindings: Map[String, ChannelBinding],
  operationBindings: Map[String, OperationBinding],
  messageBindings: Map[String, MessageBinding]
)
