package zio.http.doc.asyncapi.model

final case class Server(
                         url: String,
                         protocol: Protocol,
                         protocolVersion: Option[AsyncVersion],
                         description: Option[String],
                         variables: Map[String, ServerVariable],
                         security: List[SecurityRequirement],
                         bindings: ServerBinding
)
