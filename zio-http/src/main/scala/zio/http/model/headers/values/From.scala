//package zio.http.model.headers.values
//
///** From header value. */
//sealed trait From
//
//object From {
//  // Regex that does veery loose validation of email.
//  lazy val emailRegex = "([^ ]+@[^ ]+[.][^ ]+)".r
//
//  /**
//   * The From Header value is invalid
//   */
//  case object InvalidFromValue extends From
//
//  final case class FromValue(email: String) extends From
//
//  def toFrom(fromHeader: String): From =
//    fromHeader match {
//      case emailRegex(_) => FromValue(fromHeader)
//      case _             => InvalidFromValue
//    }
//
//  def fromFrom(from: From): String = from match {
//    case FromValue(value) => value
//    case InvalidFromValue => "z"
//  }
//}
