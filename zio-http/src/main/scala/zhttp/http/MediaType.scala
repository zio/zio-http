package zhttp.http

import java.util

case class MediaType(
  mainType: String,
  subType: String,
  compressible: Boolean = false,
  binary: Boolean = false,
  fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty,
)

object MediaType extends MimeDB {

  val memoiseMap: util.HashMap[String, Option[String]] = new util.HashMap()

  def forExtention(ext: String): Option[MediaType] = extensionMap.get(ext.toLowerCase)

  val extensionMap: Map[String, MediaType] = allMediaTypes.flatMap(m => m.fileExtensions.map(_ -> m)).toMap

  def probeContentType(name: String): Option[String] = {
    if (memoiseMap.containsKey(name))
      memoiseMap.get(name)
    else {
      val contentType = name.lastIndexOf(".") match {
        case -1 => None
        case i  => forExtention(name.substring(i + 1)).map(m => m.mainType + "/" + m.subType)
      }
      memoiseMap.put(name, contentType)
      contentType
    }
  }
}
