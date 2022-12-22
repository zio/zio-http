//package zio.http.model.headers.values
//
//import java.net.URI
//
//sealed trait ContentLocation
//
//object ContentLocation {
//  final case class ContentLocationValue(value: URI) extends ContentLocation
//  case object InvalidContentLocationValue           extends ContentLocation
//
//  def toContentLocation(value: CharSequence): ContentLocation =
//    try {
//      ContentLocationValue(new URI(value.toString))
//    } catch {
//      case _: Throwable => InvalidContentLocationValue
//    }
//
//  def fromContentLocation(contentLocation: ContentLocation): String =
//    contentLocation match {
//      case ContentLocationValue(value) => value.toString
//      case InvalidContentLocationValue => ""
//    }
//}
