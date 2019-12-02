package zio.http.doc.asyncapi.model

import java.net.URI

/*
  Representation of a channel.
  Also referred to as topics, routing keys, event types, or paths.
 */
final case class Channel[T](
  channel: URI,
  channelItem: Map[String, ChannelItem[T]]
)

/*
  The operations available on a single channel.
 */
final case class ChannelItem[T](
  $ref: String,
  description: String,
  subscribe: Operation[T],
  publish: Operation[T],
  parameters: Map[String, Parameter],
  bindings: Map[SecurityScheme, ChannelBinding]
)

/*
  Represents a publish or subscribe operation.
 */
final case class Operation[T](
  operationId: String,
  summary: String,
  description: String,
  tags: List[Tag],
  externalDocs: ExternalDocumentation,
  bindings: Map[String, OperationBinding],
  traits: List[Trait],
  message: Message[T]
)

/*
  Represents an operation trait.
 */
final case class Trait(
  operationId: String,
  summary: String,
  description: String,
  tags: List[Tag],
  externalDocs: ExternalDocumentation,
  bindings: Map[String, OperationBinding]
)
