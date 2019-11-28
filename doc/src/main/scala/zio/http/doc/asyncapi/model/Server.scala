package zio.http.doc.asyncapi.model

/**
 * An object representing a message broker, a server or any other kind of computer program capable of sending and/or
 * receiving data. This object is used to capture details such as URIs, protocols and security configuration. Variable
 * substitution can be used so that some details, for example usernames and passwords, can be injected by code
 * generation tools.
 *
 * @param url
 * @param protocol
 * @param protocolVersion
 * @param description
 * @param variables
 * @param security
 * @param bindings
 */
final case class Server(
  url: String,
  protocol: Protocol,
  protocolVersion: Option[Version],
  description: Option[String],
  variables: Map[String, ServerVariable],
  security: SecurityRequirement,
  bindings: Option[Map[SecurityScheme, ServerBinding]]
)
