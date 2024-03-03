package zio.http.endpoint

import zio.schema.codec.JsonCodec
import zio.schema.{DeriveSchema, Schema}

import zio.http.MediaType
import zio.http.codec._
import zio.http.codec.internal.TextBinaryCodec
import zio.http.template._

object HttpCodecErrorCodec {

  final case class DefaultCodecError(name: String, message: String)

  private object DefaultCodecError {
    implicit val schema: Schema[DefaultCodecError] = DeriveSchema.gen[DefaultCodecError]
  }

  private val NameExtractor    = """.*<p id="name">([^<]+)</p>.*""".r
  private val MessageExtractor = """.*<p id="message">([^<]+)</p>.*""".r

  private val domBasedSchema: Schema[HttpCodecError] =
    Schema[Dom].transformOrFail[HttpCodecError](
      dom => {
        val encoded = dom.encode

        val name = encoded match {
          case NameExtractor(name) => Some(name)
          case _                   => None
        }

        val message = encoded match {
          case MessageExtractor(message) => Some(message)
          case _                         => None
        }

        (name, message) match {
          case (Some(name), Some(message)) => Right(HttpCodecError.CustomError(name, message))
          case _                           => Left("Could not extract name and message from the DOM")
        }
      },
      {
        case HttpCodecError.CustomError(name, message) =>
          Right(
            html(
              body(
                h1("Bad Request"),
                p("There was an error decoding the request"),
                p(name, idAttr    := "name"),
                p(message, idAttr := "message"),
              ),
            ),
          )
        case e: HttpCodecError                         =>
          Right(
            html(
              body(
                h1("Bad Request"),
                p("There was an error decoding the request"),
                p(e.productPrefix, idAttr := "name"),
                p(e.getMessage(), idAttr  := "message"),
              ),
            ),
          )
      },
    )

  private val defaultCodecErrorSchema: Schema[HttpCodecError] =
    Schema[DefaultCodecError].transformOrFail[HttpCodecError](
      codecError => Right(HttpCodecError.CustomError(codecError.name, codecError.message)),
      {
        case HttpCodecError.CustomError(name, message) => Right(DefaultCodecError(name, message))
        case e: HttpCodecError                         => Right(DefaultCodecError(e.productPrefix, e.getMessage()))
      },
    )

  private val defaultHttpContentCodec: HttpContentCodec[HttpCodecError] =
    HttpContentCodec.from(
      MediaType.text.`html`      -> BinaryCodecWithSchema(TextBinaryCodec.fromSchema(domBasedSchema), domBasedSchema),
      MediaType.application.json -> BinaryCodecWithSchema(
        JsonCodec.schemaBasedBinaryCodec(defaultCodecErrorSchema),
        defaultCodecErrorSchema,
      ),
    )

  val default: HttpCodec[HttpCodecType.ResponseType, HttpCodecError] =
    ContentCodec.content(defaultHttpContentCodec) ++ StatusCodec.BadRequest
}
