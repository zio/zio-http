package zio.http.model.headers.values

sealed trait ETag
object ETag {
  case object InvalidETagValue                  extends ETag
  case class StrongETagValue(validator: String) extends ETag
  case class WeakETagValue(validator: String)   extends ETag
  def toETag(value: String): ETag = {
    value match {
      case str if str.startsWith("w/\"") && str.endsWith("\"") => WeakETagValue(str.drop(3).dropRight(1))
      case str if str.startsWith("W/\"") && str.endsWith("\"") => WeakETagValue(str.drop(3).dropRight(1))
      case str if str.startsWith("\"") && str.endsWith("\"")   => StrongETagValue(str.drop(1).dropRight(1))
      case _                                                   => InvalidETagValue
    }
  }

  def fromETag(eTag: ETag): String = {
    eTag match {
      case WeakETagValue(value)   => s"""W/"$value""""
      case StrongETagValue(value) => s""""$value""""
      case InvalidETagValue       => ""
    }
  }
}
