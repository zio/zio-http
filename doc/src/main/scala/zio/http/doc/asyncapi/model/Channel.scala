package zio.http.doc.asyncapi.model

import java.net.URI

final case class Channel[T](
  channel: URI,
  channelItem: Map[String, ChannelItem[T]]
)

final case class ChannelItem[T](
  ref: String,
  description: String,
  subscribe: Operation[T],
  publish: Operation[T],
  parameters: Option[Map[String, Parameter]],
  bindings: Option[Map[SecurityScheme, ChannelBinding]]
)

final case class Operation[T](
  operationId: String,
  summary: String,
  description: String,
  tag: List[Tag],
  externalDocs: ExternalDocumentation,
  bindings: Option[Map[String, OperationBinding]],
  traits: Option[List[Trait]],
  message: Message[T]
)

final case class Trait(
  operationId: String,
  summary: String,
  description: String,
  tags: List[Tag],
  externalDocs: ExternalDocumentation,
  bindings: Map[String, OperationBinding]
)
