//package zio.http.model.headers.values
//
//import java.time.format.DateTimeFormatter
//import java.time.{ZoneOffset, ZonedDateTime}
//
//sealed trait Date
//
///**
// * The Date general HTTP header contains the date and time at which the message
// * originated.
// */
//object Date {
//  case object InvalidDate                          extends Date
//  final case class ValidDate(value: ZonedDateTime) extends Date
//  private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME
//
//  def toDate(value: String): Date = {
//    try {
//      ValidDate(ZonedDateTime.parse(value, formatter).withZoneSameInstant(ZoneOffset.UTC))
//    } catch {
//      case _: Throwable =>
//        InvalidDate
//    }
//  }
//
//  def fromDate(value: Date): String = {
//    value match {
//      case ValidDate(date) => formatter.format(date)
//      case InvalidDate     => ""
//    }
//  }
//}
