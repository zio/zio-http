//package zio.http.model.headers.values
//
//import java.time.format.DateTimeFormatter
//import java.time.{ZoneOffset, ZonedDateTime}
//
//sealed trait LastModified
//
//object LastModified {
//  final case class LastModifiedDateTime(dateTime: ZonedDateTime) extends LastModified
//  case object InvalidLastModified                                extends LastModified
//
//  private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
//
//  def toLastModified(value: String): LastModified = {
//    try {
//      LastModifiedDateTime(ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneOffset.UTC))
//    } catch {
//      case _: Throwable =>
//        InvalidLastModified
//    }
//  }
//
//  def fromLastModified(lastModified: LastModified): String = lastModified match {
//    case LastModifiedDateTime(dateTime) => formatter.format(dateTime)
//    case InvalidLastModified            => ""
//  }
//}
