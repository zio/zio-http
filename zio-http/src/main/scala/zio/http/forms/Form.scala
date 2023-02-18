package zio.http.forms

import zio._
import zio.http.forms.FormAST._
import zio.http.forms.FormData._
import zio.http.forms.FormDecodingError._
import zio.http.forms.FormState._
import zio.http.model.Headers
import zio.stream._

import java.io.UnsupportedEncodingException
import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.{Charset, StandardCharsets}
import java.security.SecureRandom

/**
 * Represents a form that can be either multipart or url encoded.
 */
final case class Form(formData: Chunk[FormData]) {

  def +(field: FormData): Form = append(field)

  def append(field: FormData): Form = Form(formData :+ field)

  def get(name: String): Option[FormData] = formData.find(_.name == name)

  def encodeAsURLEncoded(charset: Charset = StandardCharsets.UTF_8): String = {

    def makePair(name: String, value: String): String = {
      val encodedName  = URLEncoder.encode(name, charset.name())
      val encodedValue = URLEncoder.encode(value, charset.name())
      s"$encodedName=$encodedValue"
    }

    val urlEncoded = formData.foldLeft(Chunk.empty[String]) {
      case (accum, FormData.Text(k, v, _, _)) => accum :+ makePair(k, v)
      case (accum, FormData.Simple(k, v))     => accum :+ makePair(k, v)
      case (accum, _)                         => accum
    }

    urlEncoded.mkString("&")
  }

  def encodeAsMultipartBytes(
    charset: Charset = StandardCharsets.UTF_8,
    rng: () => String = () => new SecureRandom().nextLong().toString(),
  ): (Headers, Chunk[Byte]) = {

    val boundary              = Boundary.generate(rng)
    val encapsulatingBoundary = EncapsulatingBoundary(boundary)
    val closingBoundary       = ClosingBoundary(boundary)

    val ast = formData.flatMap {
      case fd @ Simple(name, value) =>
        Chunk(
          encapsulatingBoundary,
          EoL,
          Header.contentDisposition(name),
          EoL,
          Header.contentType(fd.contentType),
          EoL,
          EoL,
          Content(Chunk.fromArray(value.getBytes(charset))),
          EoL,
        )

      case Text(name, value, contentType, filename)                    =>
        Chunk(
          encapsulatingBoundary,
          EoL,
          Header.contentDisposition(name, filename),
          EoL,
          Header.contentType(contentType),
          EoL,
          EoL,
          Content(Chunk.fromArray(value.getBytes(charset))),
          EoL,
        )
      case Binary(name, data, contentType, transferEncoding, filename) =>
        val xferEncoding =
          transferEncoding.map(enc => Chunk(Header.contentTransferEncoding(enc), EoL)).getOrElse(Chunk.empty)

        Chunk(
          encapsulatingBoundary,
          EoL,
          Header.contentDisposition(name, filename),
          EoL,
          Header.contentType(contentType),
          EoL,
        ) ++ xferEncoding ++
          Chunk(
            EoL,
            Content(data),
            EoL,
          )
    } ++ Chunk(closingBoundary, EoL)

    boundary.contentTypeHeader -> ast.flatMap(_.bytes)
  }
}

object Form {

  def apply(formData: FormData*): Form = Form(Chunk.fromIterable(formData))

  def fromStrings(formData: (String, String)*): Form = apply(
    formData.map(pair => FormData.Simple(pair._1, pair._2)): _*,
  )

  def fromMultipartBytes(
    bytes: Chunk[Byte],
    charset: Charset = StandardCharsets.UTF_8,
  ): ZIO[Any, FormDecodingError, Form] = {
    def process(boundary: Boundary) = ZStream
      .fromChunk(bytes)
      .mapAccum(FormState.fromBoundary(boundary)) { (state, byte) =>
        state match {
          case BoundaryClosed(tree)       => (FormState.fromBoundary(boundary), tree)
          case BoundaryEncapsulated(tree) => (FormState.fromBoundary(boundary, Some(byte)), tree)
          case buffer: FormStateBuffer    =>
            val state = buffer.append(byte)
            state match {
              case BoundaryClosed(prevContent) => (state, prevContent)
              case _                           => (state, Chunk.empty[FormAST])
            }
        }
      }
      .collectZIO {
        case chunk if chunk.nonEmpty => FormData.fromFormAST(chunk, charset)
      }
      .runCollect
      .map(apply)

    for {
      boundary <- ZIO
        .fromOption(Boundary.fromContent(bytes, charset))
        .mapError(_ => FormDecodingError.BoundaryNotFoundInContent)
      form     <- process(boundary)
    } yield form
  }

  def fromURLEncoded(encoded: String, encoding: Charset): ZIO[Any, FormDecodingError, Form] = {
    val fields = ZIO.foreach(encoded.split("&")) { pair =>
      val array = pair.split("=")
      if (array.length == 2)
        ZIO
          .attempt((URLDecoder.decode(array(0), encoding.name()), URLDecoder.decode(array(1), encoding.name)))
          .mapError {
            case e: UnsupportedEncodingException => InvalidCharset(e.getMessage)
            case e                               => InvalidURLEncodedFormat(e.getMessage)
          }
      else
        ZIO.fail(
          InvalidURLEncodedFormat(s"Invalid form field.  Expected: '{name}={value}'', Got '$pair' instead."),
        )
    }

    fields.map(fields => Form(Chunk.fromArray(fields.map(pair => FormData.Simple(pair._1, pair._2)))))
  }

}
