package zhttp.experiment

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.multipart
import zio.Chunk

sealed trait Part

object Part {
  def fromHTTPData(data: multipart.InterfaceHttpData): Part = data match {
    case data: multipart.HttpData =>
      data match {
        case attribute: multipart.Attribute =>
          val b     = attribute.content()
          val count = b.readableBytes()
          val res   = Part.Attribute(attribute.getName, Option(attribute.getValue))
          b.readBytes(count)
          res

        case upload: multipart.FileUpload =>
          val b     = upload.content()
          val count = b.readableBytes()
          Part.FileData(Chunk.fromArray(ByteBufUtil.getBytes(b.readBytes(count))), Option(upload.getFilename))
        case _                            => Part.Empty
      }
    case _                        => Part.Empty
  }
  case class FileData(content: Chunk[Byte], fileName: Option[String]) extends Part
  case class Attribute(name: String, value: Option[String]) extends Part
  case object Empty                                         extends Part
}
