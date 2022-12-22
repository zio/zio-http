//package zio.http.model.headers.values
//
//import zio.http.URL
//
///** Origin header value. */
//sealed trait Origin
//
//object Origin {
//
//  /** The Origin header value is privacy sensitive or is an opaque origin. */
//  case object OriginNull extends Origin
//
//  /** The Origin header value contains scheme, host and maybe port. */
//  final case class OriginValue(scheme: String, host: String, port: Option[Int]) extends Origin
//
//  /** The Origin header value is invalid. */
//  case object InvalidOriginValue extends Origin
//
//  def fromOrigin(origin: Origin): String = {
//    origin match {
//      case OriginNull                           => "null"
//      case OriginValue(scheme, host, maybePort) =>
//        maybePort match {
//          case Some(port) => s"$scheme://$host:$port"
//          case None       => s"$scheme://$host"
//        }
//      case InvalidOriginValue                   => ""
//    }
//  }
//
//  def toOrigin(value: String): Origin =
//    if (value == "null") OriginNull
//    else
//      URL.fromString(value) match {
//        case Left(_)                                              => InvalidOriginValue
//        case Right(url) if url.host.isEmpty || url.scheme.isEmpty => InvalidOriginValue
//        case Right(url) => OriginValue(url.scheme.get.encode, url.host.get, url.port)
//      }
//}
