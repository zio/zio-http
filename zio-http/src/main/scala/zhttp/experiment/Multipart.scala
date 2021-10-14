package zhttp.experiment

import zhttp.experiment.Part.FileData
import zio.stream.ZStream

case class MultiPart(data: ZStream[Any, Nothing, Part]) { self =>
  def getFile(fileName: String): ZStream[Any, Nothing, FileData] = self.data.filter {
    case FileData(_, fn)      =>
      fn match {
        case Some(value) => value == fileName
        case None        => false
      }
    case Part.Attribute(_, _) => false
  }.map {
    case m @ FileData(_, _)      => m
    case Part.Attribute(name, _) => FileData(ZStream.empty, Option(name))
  }
}

sealed trait Part
object Part {
  case class FileData(content: ZStream[Any, Nothing, Byte], fileName: Option[String]) extends Part
  case class Attribute(name: String, value: Option[String])                           extends Part
}
