package zhttp.http

import java.util

final case class MediaType(
  mainType: String,
  subType: String,
  compressible: Boolean = false,
  binary: Boolean = false,
  fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty,
) {
  def fullType: String = s"$mainType/$subType"
}

object MediaType extends MimeDB {

  private val memoizeMap: util.HashMap[String, Option[String]] = new util.HashMap()

  private val extensionMap: Map[String, MediaType] = allMediaTypes.flatMap(m => m.fileExtensions.map(_ -> m)).toMap

  def probe(ext: String): Option[MediaType] = extensionMap.get(ext.toLowerCase)

  def probeContentType(name: String, cache: Boolean = false): Option[String] = {
    if (memoizeMap.containsKey(name) && cache) {
      memoizeMap.get(name)
    } else {
      val contentType = name.lastIndexOf(".") match {
        case -1 => None
        case i  => probe(name.substring(i + 1)).map(_.fullType)
      }
      if (cache) {
        memoizeMap.put(name, contentType)
      }
      contentType
    }
  }
}
