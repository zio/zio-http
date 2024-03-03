package zio.http.endpoint

import zio.Chunk

import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http.codec._
import zio.http.template.{Dom, body, h1, html, p}
import zio.http.{MediaType, Response}

final case class CodecErrorHandler[A](
  log: Option[HttpCodecError => String],
  mapError: MapError[A],
)

object CodecErrorHandler {

  final case class DefaultCodecError(message: String)

  object DefaultCodecError {
    implicit val schema: Schema[DefaultCodecError] = DeriveSchema.gen[DefaultCodecError]
  }

  val default: CodecErrorHandler[_] = CodecErrorHandler(
    None,
    MapError(
      { (e, mts) =>
        mts.collectFirst {
          case mt if mt.mediaType.matches(MediaType.application.json, ignoreParameters = true) =>
            Right(DefaultCodecError(e.getMessage()))
          case mt if mt.mediaType.matches(MediaType.text.html, ignoreParameters = true)        =>
            Left(
              html(
                body(
                  h1("Bad Request"),
                  p("There was an error decoding the request"),
                  p(e.getMessage()),
                ),
              ),
            )

        } match {
          case Some(response) =>
            response
          case None           =>
            Left(
              html(
                body(
                  h1("Bad Request"),
                  p("There was an error decoding the request"),
                  p(e.getMessage()),
                ),
              ),
            )
        }
      },
      (ContentCodec.content[Dom](HttpContentCodec.html.htmlCodec) | ContentCodec
        .content[DefaultCodecError]("codec-error", MediaType.application.json)) ++ StatusCodec.BadRequest,
    ),
  )

}

final case class MapError[Err](
  f: (HttpCodecError, Chunk[MediaTypeWithQFactor]) => Err,
  codec: HttpCodec[HttpCodecType.ResponseType, Err],
) {
  def mapError(e: HttpCodecError, mediaTypes: Chunk[MediaTypeWithQFactor]): Response = {
    codec.encodeResponse(f(e, mediaTypes), mediaTypes)
  }
}
