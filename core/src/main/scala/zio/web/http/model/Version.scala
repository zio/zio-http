package zio.web.http.model

sealed abstract class Version(val name: String) {
  override def toString(): String = s"Version.$name"
}

object Version {
  object V1_1 extends Version("HTTP/1.1")
  object V2   extends Version("HTTP/2.0")

  def fromString(version: String): Version =
    version match {
      case "HTTP/1.1" => Version.V1_1
      case "HTTP/2.0" => Version.V2 //TODO: actually not supported yet
      case _          => throw new IllegalArgumentException(s"Unable to handle version: $version")
    }
}
