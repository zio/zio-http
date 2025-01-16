package zio.http.gen.sbt

sealed trait Format
object Format {
  case object YAML extends Format
  case object JSON extends Format
  def fromFileName(fileName: String): Format =
    if (fileName.endsWith("yml") || fileName.endsWith("yaml")) YAML
    else if (fileName.endsWith("json")) JSON
    else throw new Exception(s"Unknown format for file $fileName")
}
