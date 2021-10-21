package zhttp.experiment

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.multipart
import zio.Chunk

sealed trait Part

object Part {
  def fromHTTPData(data: multipart.InterfaceHttpData): Part = data match {
    case data: multipart.HttpData =>
      data match {
        case attribute: multipart.Attribute => Part.Attribute(attribute.getName, Option(attribute.getValue))
        case upload: multipart.FileUpload   =>
          Part.FileData(Chunk.fromArray(ByteBufUtil.getBytes(upload.content())), Option(upload.getFilename))
        case _                              => Part.Empty
      }
    case _                        => Part.Empty
  }
  case class FileData(content: Chunk[Byte], fileName: Option[String]) extends Part
  case class Attribute(name: String, value: Option[String]) extends Part
  case object Empty                                         extends Part
}
