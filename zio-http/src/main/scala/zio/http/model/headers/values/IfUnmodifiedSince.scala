//package zio.http.model.headers.values
//
//import java.time.format.DateTimeFormatter
//import java.time.{ZoneOffset, ZonedDateTime}
//
//sealed trait IfUnmodifiedSince
//
///**
// * If-Unmodified-Since request header makes the request for the resource
// * conditional: the server will send the requested resource or accept it in the
// * case of a POST or another non-safe method only if the resource has not been
// * modified after the date specified by this HTTP header.
// */
//object IfUnmodifiedSince {
//  final case class UnmodifiedSince(value: ZonedDateTime) extends IfUnmodifiedSince
//
//  case object InvalidUnmodifiedSince extends IfUnmodifiedSince
//
//  private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
//
//  def toIfUnmodifiedSince(value: String): IfUnmodifiedSince =
//    try {
//      UnmodifiedSince(ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneOffset.UTC))
//    } catch {
//      case _: Throwable =>
//        InvalidUnmodifiedSince
//    }
//
//  def fromIfUnmodifiedSince(ifModifiedSince: IfUnmodifiedSince): String = ifModifiedSince match {
//    case UnmodifiedSince(value) => formatter.format(value)
//    case InvalidUnmodifiedSince => ""
//  }
//
//}
