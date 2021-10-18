package zhttp.experiment

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.multipart.{Attribute => JAttribute, FileUpload, HttpData, InterfaceHttpData}
import zio.Chunk

sealed trait Part

object Part {
  def fromHTTPData(data: InterfaceHttpData): Part = data match {
    case data: HttpData =>
      data match {
        case attribute: JAttribute => Part.Attribute(attribute.getName, Option(attribute.getValue))
        case upload: FileUpload    =>
          Part.FileData(Chunk.fromArray(ByteBufUtil.getBytes(upload.content())), Option(upload.getFilename))
        case _                     => Part.Empty
      }
    case _              => Part.Empty
  }
  case class FileData(content: Chunk[Byte], fileName: Option[String]) extends Part
  case class Attribute(name: String, value: Option[String]) extends Part
  case object Empty                                         extends Part
}
