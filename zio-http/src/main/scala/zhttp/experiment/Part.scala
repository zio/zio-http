package zhttp.experiment

import zio.{Chunk}

//case class MultipartData(parts: Queue[Part])
sealed trait Part

object Part {
  case class FileData(content: Chunk[Byte], fileName: Option[String]) extends Part
  case class Attribute(name: String, value: Option[String])           extends Part
  case object Empty                                                   extends Part
}
