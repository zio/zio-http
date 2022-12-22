//package zio.http.model.headers.values
//
//import zio.Chunk
//
//sealed trait IfNoneMatch
//
//object IfNoneMatch {
//  case object Any extends IfNoneMatch
//
//  case object None extends IfNoneMatch
//
//  final case class ETags(etags: Chunk[String]) extends IfNoneMatch
//
//  def toIfNoneMatch(value: String): IfNoneMatch = {
//    val etags = value.split(",").map(_.trim).toList
//    etags match {
//      case "*" :: Nil => Any
//      case "" :: Nil  => None
//      case _          => ETags(Chunk.fromIterable(etags))
//    }
//  }
//
//  def fromIfNoneMatch(ifMatch: IfNoneMatch): String = ifMatch match {
//    case Any          => "*"
//    case None         => ""
//    case ETags(etags) => etags.mkString(",")
//  }
//}
