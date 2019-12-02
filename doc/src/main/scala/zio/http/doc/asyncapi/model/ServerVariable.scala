package zio.http.doc.asyncapi.model

/**
 *  An object representing a Server Variable for server URL template substitution
 *
 * @param enum  An enumeration of string values to be used if the substitution options are from a limited set
 * @param default The default value to use for substitution, and to send, if an alternate value is not supplied.
 * @param description An optional description for the server variable. CommonMark syntax MAY be used for rich text representation.
 * @param examples An array of examples of the server variable.
 */
final case class ServerVariable(
  enum: List[String],
  default: String,
  description: String,
  examples: List[String]
)
