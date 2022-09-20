package zio.http.model

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

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
  private val extensionMap: Map[String, MediaType]   = allMediaTypes.flatMap(m => m.fileExtensions.map(_ -> m)).toMap
  private val contentTypeMap: Map[String, MediaType] = allMediaTypes.map(m => m.fullType -> m).toMap

  def forContentType(contentType: String): Option[MediaType] = contentTypeMap.get(contentType)

  def forFileExtension(ext: String): Option[MediaType] = extensionMap.get(ext)
}
