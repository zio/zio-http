//package zio.http.model.headers.values
//
///** Vary header value. */
//sealed trait Vary
//
//object Vary {
//  case class HeadersVaryValue(headers: List[String]) extends Vary
//  case object StarVary                               extends Vary
//  case object InvalidVaryValue                       extends Vary
//
//  def toVary(value: String): Vary = {
//    value.toLowerCase().split("[, ]+").toList match {
//      case List("*")                               => StarVary
//      case list if list.nonEmpty && value.nonEmpty => HeadersVaryValue(list.map(_.trim))
//      case _                                       => InvalidVaryValue
//    }
//  }
//
//  def fromVary(vary: Vary): String = {
//    vary match {
//      case StarVary               => "*"
//      case HeadersVaryValue(list) => list.mkString(", ")
//      case InvalidVaryValue       => ""
//    }
//  }
//}
