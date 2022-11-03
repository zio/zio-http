package zio.http.model.headers.values

import zio.http.model.MediaType

sealed trait ContentType extends Product with Serializable { self =>
  def toStringValue: String = ContentType.fromContentType(self)
}

object ContentType {
  final case class ContentTypeValue(value: MediaType) extends ContentType
  case object InvalidContentType                      extends ContentType

  def toContentType(s: CharSequence): ContentType =
    MediaType.forContentType(s.toString) match {
      case Some(value) => ContentTypeValue(value)
      case None        => InvalidContentType
    }

  def fromContentType(contentType: ContentType): String =
    contentType match {
      case ContentTypeValue(value) => value.fullType
      case InvalidContentType      => ""
    }

  def fromMediaType(mediaType: MediaType): ContentType = ContentTypeValue(mediaType)

}
