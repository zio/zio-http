package zio.http.doc.asyncapi.model

case class ServerVariable(
  enum: Option[List[String]],
  default: Option[String],
  description: Option[String],
  examples: Option[String]
)
