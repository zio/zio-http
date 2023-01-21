package zio.http.forms

import zio._
import zio.http.forms.FormAST._
import zio.http.forms.FormDecodingError._
import zio.http.model.MediaType

import java.nio.charset.Charset

sealed trait FormData {
  def name: String
  def contentType: MediaType
  def filename: Option[String]

  def valueAsString: Option[String] = this match {
    case FormData.Text(_, value, _, _) => Some(value)
    case FormData.Simple(_, value)     => Some(value)
    case _                             => None
  }
}

object FormData {

  /**
   * A binary form data part.
   *
   * @param name
   *   Name of this form data part. This is the value of the `name` field in the
   *   `Content-Disposition` within this part.
   * @param data
   *   The data of this form data part. This is the data between the headers and
   *   the boundary.
   * @param contentType
   *   The content type of this form data part. This is the value of the
   *   `Content-Type` with in this part.
   * @param transferEncoding
   *   The transfer encoding of this form data part. This is the value of the
   *   `Content-Transfer-Encoding` within this part. IMPORTANT NOTE: The data is
   *   not encoded in any way relative to the provided `transferEncoding`. It is
   *   the responsibility of the user to encode the `data` accordingly.
   * @param filename
   */
  final case class Binary(
    name: String,
    data: Chunk[Byte],
    contentType: MediaType,
    transferEncoding: Option[ContentTransferEncoding] = None,
    filename: Option[String] = None,
  ) extends FormData

  final case class Text(
    name: String,
    value: String,
    contentType: MediaType,
    filename: Option[String] = None,
  ) extends FormData

  final case class Simple(name: String, value: String) extends FormData {
    override val contentType: MediaType   = MediaType.text.plain
    override val filename: Option[String] = None
  }

  def fromFormAST(ast: Chunk[FormAST], defaultCharset: Charset = `UTF-8`): ZIO[Any, FormDecodingError, FormData] = {
    val extract =
      ast.foldLeft((Option.empty[Header], Option.empty[Header], Option.empty[Header], Option.empty[Content])) {
        case (accum, header: Header) if header.name == "Content-Disposition"       =>
          (Some(header), accum._2, accum._3, accum._4)
        case (accum, content: Content)                                             =>
          (accum._1, accum._2, accum._3, Some(content))
        case (accum, header: Header) if header.name == "Content-Type"              =>
          (accum._1, Some(header), accum._3, accum._4)
        case (accum, header: Header) if header.name == "Content-Transfer-Encoding" =>
          (accum._1, accum._2, Some(header), accum._4)
        case (accum, _)                                                            => accum
      }

    for {
      disposition <- ZIO.fromOption(extract._1).mapError(_ => FormDataMissingContentDisposition)
      name    <- ZIO.fromOption(extract._1.flatMap(_.fields.get("name"))).mapError(_ => ContentDispositionMissingName)
      charset <- ZIO
        .attempt(extract._2.flatMap(x => x.fields.get("charset").map(Charset.forName)).getOrElse(defaultCharset))
        .mapError(e => InvalidCharset(e.getMessage))
      content          = extract._4.map(_.bytes).getOrElse(Chunk.empty)
      contentType      = extract._2
        .flatMap(x => MediaType.forContentType(x.preposition))
        .getOrElse(MediaType.text.plain)
      transferEncoding = extract._3
        .flatMap(x => ContentTransferEncoding.parse(x.preposition))

    } yield
      if (!contentType.binary)
        Text(name, new String(content.toArray, charset), contentType, disposition.fields.get("filename"))
      else Binary(name, content, contentType, transferEncoding, disposition.fields.get("filename"))
  }

  def textField(name: String, value: String, mediaType: MediaType = MediaType.text.plain): FormData =
    Text(name, value, mediaType, None)

  def simpleField(name: String, value: String): FormData = Simple(name, value)

  def binaryField(
    name: String,
    data: Chunk[Byte],
    mediaType: MediaType,
    transferEncoding: Option[ContentTransferEncoding] = None,
    filename: Option[String] = None,
  ): FormData = Binary(name, data, mediaType, transferEncoding, filename)
}
