package zio.http.model.headers.values

import zio.Chunk

sealed trait IfMatch

object IfMatch {
  case object Any                              extends IfMatch
  case object None                             extends IfMatch
  final case class ETags(etags: Chunk[String]) extends IfMatch

  def toIfMatch(value: String): IfMatch = {
    val etags = value.split(",").map(_.trim).toList
    etags match {
      case "*" :: Nil => Any
      case "" :: Nil  => None
      case _          => ETags(Chunk.fromIterable(etags))
    }
  }

  def fromIfMatch(ifMatch: IfMatch): String = ifMatch match {
    case Any          => "*"
    case None         => ""
    case ETags(etags) => etags.mkString(",")
  }

}
