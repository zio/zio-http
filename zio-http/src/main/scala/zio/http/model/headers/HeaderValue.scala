package zio.http.model.headers

import zio.http.model.headers.HeaderValue.ETag.{InvalidETagValue, StrongETagValue, WeakETagValue}

object HeaderValue {
  sealed trait ETag
  object ETag {
    case class StrongETagValue(validator: String) extends ETag
    case class WeakETagValue(validator: String)   extends ETag
  }

  def toETag(value: String): ETag = {
    if (value.startsWith("W\\") || value.startsWith("w\\"))
      WeakETagValue(value.substring(2))
    else
      StrongETagValue(value)
  }
}
