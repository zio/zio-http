package zio.http.endpoint.http

import zio.http._
import zio.http.endpoint.openapi.JsonSchema

/** A `.http` file: a list of [[HttpEndpoint]] requests that render as text. */
final case class HttpFile(endpoints: List[HttpEndpoint]) {

  /**
   * Returns a file with the endpoints of this file followed by those of `that`.
   */
  def ++(that: HttpFile): HttpFile = HttpFile(endpoints ++ that.endpoints)

  /** Renders each endpoint and joins them with a blank line between them. */
  def render: String = endpoints.map(_.render).mkString("\n\n")
}

/**
 * One request of an [[HttpFile]]: a method and path, header names, an optional
 * JSON body schema, the variables the request uses and an optional comment.
 */
final case class HttpEndpoint(
  method: Method,
  path: String,
  headers: Seq[String],
  requestBody: Option[JsonSchema],
  variables: Seq[HttpVariable],
  docString: Option[String],
) {

  /**
   * Renders the comment lines, the variable definitions, the request line, the
   * header lines and, for a JSON body, the body template.
   */
  def render: String =
    renderDoc + renderVariables + renderPath + renderHeaders + renderRequestBody

  private def renderRequestBody: String = {
    requestBody match {
      case None         => ""
      case Some(schema) =>
        renderSchema(schema)
    }
  }

  private def renderSchema(schema: JsonSchema, name: Option[String] = None): String =
    schema match {
      case JsonSchema.AnnotatedSchema(schema, _) => renderSchema(schema)
      case JsonSchema.RefSchema(_)               => throw new Exception("RefSchema not supported")
      case JsonSchema.OneOfSchema(_)             => throw new Exception("OneOfSchema not supported")
      case JsonSchema.AllOfSchema(_)             => throw new Exception("AllOfSchema not supported")
      case JsonSchema.AnyOfSchema(_)             => throw new Exception("AnyOfSchema not supported")
      case JsonSchema.Number(_, _, _, _, _, _)   => s""""${getName(name)}": {{${getName(name)}}}"""
      case JsonSchema.Integer(_, _, _, _, _, _)  => s""""${getName(name)}": {{${getName(name)}}}"""
      case JsonSchema.String(_, _, _, _)         => s""""${getName(name)}": {{${getName(name)}}}"""
      case JsonSchema.Boolean                    => s""""${getName(name)}": {{${getName(name)}}}"""
      case JsonSchema.ArrayType(_, _, _)         => s""""${getName(name)}": {{${getName(name)}}}"""
      case JsonSchema.Object(properties, _, _)   =>
        if (properties.isEmpty) ""
        else {
          val fields = properties.map { case (name, schema) => renderSchema(schema, Some(name)) }.mkString(",\n")
          // TODO: This has to be removed when we support other content types
          if (name.isEmpty) s"\nContent-type: application/json\n\n{\n$fields\n}"
          else s""""${getName(name)}": {\n$fields\n}"""
        }
      case JsonSchema.Enum(_)                    => s""""${getName(name)}": {{${getName(name)}}}"""
      case JsonSchema.Null                       => ""
      case JsonSchema.AnyJson                    => ""
    }

  private def getName(name: Option[String]) = { name.getOrElse(throw new IllegalArgumentException("name is required")) }

  private def renderDoc =
    docString match {
      case None      =>
        ""
      case Some(doc) =>
        doc.split("\n").map(line => s"# $line").mkString("\n", "\n", "\n")
    }

  private def renderVariables =
    if (variables.isEmpty) ""
    else variables.distinct.map(_.render).mkString("\n", "\n", "\n\n")

  private def renderHeaders =
    if (headers.isEmpty) ""
    else headers.map(h => s"${h.capitalize}: {{${h.capitalize}}}").mkString("\n", "\n", "")

  private def renderPath = {
    if (method == Method.ANY) {
      s"POST $path"
    } else {
      s"${method.render} $path"
    }
  }
}

/**
 * A variable of an [[HttpEndpoint]], named `name`, with an optional example
 * `value` and an optional comment `docString`.
 */
final case class HttpVariable(name: String, value: Option[String], docString: Option[String] = None) {

  /**
   * Renders the definition `@name=value`, with `<no value>` when there is no
   * value, after the comment lines of `docString` when it is set.
   */
  def render = {
    val variable = s"@$name=${value.getOrElse("<no value>")}"
    if (docString.isDefined) {
      docString.get.split("\n").map(line => s"# $line").mkString("", "\n", "\n") + variable
    } else variable
  }

}
