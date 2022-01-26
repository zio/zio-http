package zhttp.http

case class MediaType(
  mainType: String,
  val subType: String,
  val compressible: Boolean = false,
  val binary: Boolean = false,
  val fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty,
)

object MediaType extends MimeDB {
  def forExtention(ext: String): Option[MediaType] = extensionMap.get(ext.toLowerCase)

  val extensionMap: Map[String, MediaType] = allMediaTypes.flatMap(m => m.fileExtensions.map(_ -> m)).toMap

  def probeContentType(name: String): Option[String] = name.lastIndexOf(".") match {
    case -1 => None
    case i  => forExtention(name.substring(i + 1)).map(m => m.mainType + "/" + m.subType)
  }
}
