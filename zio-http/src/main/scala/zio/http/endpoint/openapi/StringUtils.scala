package zio.http.endpoint.openapi

private[openapi] object StringUtils {
  def pascalCase(string: String): String =
    string.split("[-_ ]+").map(_.toLowerCase.capitalize).mkString

  def camelCase(string: String): String = {
    val parts = string.split("[-_ ]+").map(_.toLowerCase)
    parts.headOption.getOrElse("") + parts.tail.map(_.capitalize).mkString
  }
}
