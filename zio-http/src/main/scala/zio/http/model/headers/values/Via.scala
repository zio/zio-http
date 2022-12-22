//package zio.http.model.headers.values
//
//import zio.Chunk
//import zio.http.model.headers.values.Via.ReceivedProtocol.InvalidProtocol
//
//sealed trait Via
//
///**
// * The Via general header is added by proxies, both forward and reverse, and can
// * appear in the request or response headers. It is used for tracking message
// * forwards, avoiding request loops, and identifying the protocol capabilities
// * of senders along the request/response chain
// */
//object Via {
//  sealed trait ReceivedProtocol
//  object ReceivedProtocol {
//    final case class Version(version: String)                           extends ReceivedProtocol
//    final case class ProtocolVersion(protocol: String, version: String) extends ReceivedProtocol
//
//    case object InvalidProtocol extends ReceivedProtocol
//  }
//
//  final case class ViaValues(values: Chunk[Via]) extends Via
//  final case class DetailedValue(receivedProtocol: ReceivedProtocol, receivedBy: String, comment: Option[String])
//      extends Via
//  case object InvalidVia                         extends Via
//
//  def toVia(values: String): Via = if (values.isEmpty) InvalidVia
//  else {
//    val viaValues = values.split(",").map(_.trim).map { value =>
//      value.split(" ").toList match {
//        case receivedProtocol :: receivedBy :: Nil            =>
//          val rp = toReceivedProtocol(receivedProtocol)
//          if (rp == InvalidProtocol) InvalidVia
//          else DetailedValue(rp, receivedBy, None)
//        case receivedProtocol :: receivedBy :: comment :: Nil =>
//          val rp = toReceivedProtocol(receivedProtocol)
//          if (rp == InvalidProtocol) InvalidVia
//          else DetailedValue(rp, receivedBy, Some(comment))
//        case _                                                => InvalidVia
//      }
//    }
//    ViaValues(Chunk.fromArray(viaValues))
//  }
//
//  def fromVia(via: Via): String = via match {
//    case ViaValues(values)                                    =>
//      values.map(fromVia).mkString(", ")
//    case DetailedValue(receivedProtocol, receivedBy, comment) =>
//      s"${fromReceivedProtocol(receivedProtocol)} $receivedBy ${comment.getOrElse("")}"
//    case InvalidVia                                           => ""
//  }
//
//  private def fromReceivedProtocol(receivedProtocol: ReceivedProtocol): String = receivedProtocol match {
//    case ReceivedProtocol.Version(version)                   => version
//    case ReceivedProtocol.ProtocolVersion(protocol, version) => s"$protocol/$version"
//    case ReceivedProtocol.InvalidProtocol                    => ""
//  }
//
//  private def toReceivedProtocol(value: String): ReceivedProtocol = {
//    value.split("/").toList match {
//      case version :: Nil             => ReceivedProtocol.Version(version)
//      case protocol :: version :: Nil => ReceivedProtocol.ProtocolVersion(protocol, version)
//      case _                          => InvalidProtocol
//    }
//  }
//
//}
