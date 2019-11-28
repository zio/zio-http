package zio.http.doc.asyncapi.model

/**
 * Lists the required security schemes to execute this operation
 * @param name Security scheme
 * @param values List of values specific to the security scheme
 */
case class SecurityRequirement(
  name: SecurityScheme,
  values: List[String]
)
