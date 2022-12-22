//package zio.http.model.headers.values
//
//import java.time.format.DateTimeFormatter
//import java.time.{ZoneOffset, ZonedDateTime}
//
//sealed trait IfModifiedSince
//
//object IfModifiedSince {
//  final case class ModifiedSince(value: ZonedDateTime) extends IfModifiedSince
//  case object InvalidModifiedSince                     extends IfModifiedSince
//
//  private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
//
//  def toIfModifiedSince(value: String): IfModifiedSince =
//    try {
//      ModifiedSince(ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneOffset.UTC))
//    } catch {
//      case _: Throwable =>
//        InvalidModifiedSince
//    }
//
//  def fromIfModifiedSince(ifModifiedSince: IfModifiedSince): String = ifModifiedSince match {
//    case ModifiedSince(value) => formatter.format(value)
//    case InvalidModifiedSince => ""
//  }
//}
