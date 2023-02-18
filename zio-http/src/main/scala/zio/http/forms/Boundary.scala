package zio.http.forms

import zio.Chunk
import zio.http.forms.FormAST.Header
import zio.http.model.{Headers, MediaType}

import java.nio.charset.{Charset, StandardCharsets}
import java.security.SecureRandom

final case class Boundary(id: String, charset: Charset = StandardCharsets.UTF_8) {

  def isEncapsulating(bytes: Chunk[Byte]): Boolean = bytes == encapsulationBoundaryBytes

  def isClosing(bytes: Chunk[Byte]): Boolean = bytes == closingBoundaryBytes

  def contentTypeHeader: Headers = Headers.contentType(s"${MediaType.multipart.`form-data`.fullType}; boundary=$id")

  val encapsulationBoundary: String = s"--$id"

  val closingBoundary: String = s"--$id--"

  private[forms] val encapsulationBoundaryBytes = Chunk.fromArray(encapsulationBoundary.getBytes(charset))

  private[forms] val closingBoundaryBytes = Chunk.fromArray(closingBoundary.getBytes(charset))

}

object Boundary {

  def generate(rng: () => String = () => new SecureRandom().nextLong.toString): Boundary =
    Boundary(s"(((${rng().toString()})))")

  def fromContent(content: Chunk[Byte], charset: Charset = StandardCharsets.UTF_8): Option[Boundary] = {
    var i = 0
    var j = 0

    val boundaryBytes = content.foldWhile(Chunk.empty[Byte])(_ => i < 2) {
      case (buffer, byte) if byte == '\r' || byte == '\n' =>
        i = i + 1
        buffer
      case (buffer, byte) if byte == '-' && j < 2         =>
        j = j + 1
        buffer
      case (buffer, byte)                                 =>
        buffer :+ byte
    }
    if (i == 2 && j == 2)
      Some(Boundary(new String(boundaryBytes.toArray, charset)))
    else Option.empty
  }

  def fromHeaders(headers: Headers): Option[Boundary] = {

    val charset =
      headers.contentType
        .flatMap(value => Header("Content-Type", value.toString()).fields.get("charset"))
        .map(Charset.forName)
        .getOrElse(StandardCharsets.UTF_8)

    for {
      disp     <- headers.contentDisposition
      boundary <- Header("Content-Disposition", disp.toString()).fields.get("boundary")

    } yield Boundary(boundary, charset)

  }

}
