package zio.http.codec

import scala.collection.immutable.ListMap

import zio._

import zio.stream.ZPipeline

import zio.schema.codec._
import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.codec.internal.TextBinaryCodec
import zio.http.internal.HeaderOps
import zio.http.template._

final case class HttpContentCodec[A](
  choices: ListMap[MediaType, BinaryCodecWithSchema[A]],
) { self =>

  private var lookupCache: Map[MediaType, Option[BinaryCodecWithSchema[A]]] = Map.empty

  /**
   * A right biased merge of two HttpContentCodecs.
   */
  def ++(that: HttpContentCodec[A]): HttpContentCodec[A] =
    HttpContentCodec(choices ++ that.choices)

  def decodeRequest(request: Request): Task[A] = {
    val contentType = mediaTypeFromContentTypeHeader(request)
    lookup(contentType) match {
      case Some(codec) =>
        request.body.asChunk.flatMap { bytes =>
          ZIO.fromEither(codec.codec.decode(bytes))
        }
      case None        =>
        ZIO.fail(throw new IllegalArgumentException(s"No codec found for content type $contentType"))
    }
  }

  def decodeResponse(response: Response): Task[A] = {
    val contentType = mediaTypeFromContentTypeHeader(response)
    lookup(contentType) match {
      case Some(codec) =>
        response.body.asChunk.flatMap { bytes =>
          ZIO.fromEither(codec.codec.decode(bytes))
        }
      case None        =>
        ZIO.fail(throw new IllegalArgumentException(s"No codec found for content type $contentType"))
    }
  }

  private def mediaTypeFromContentTypeHeader(header: HeaderOps[_]) = {
    if (header.headers.contains(Header.ContentType.name)) {
      val contentType = header.headers.getUnsafe(Header.ContentType.name)
      if (MediaType.contentTypeMap.contains(contentType)) {
        MediaType.contentTypeMap(contentType)
      } else {
        MediaType.unsafeParseCustomMediaType(contentType)
      }
    } else {
      MediaType.application.`json`
    }
  }

  def encode(value: A): Either[String, Body] = {
    if (choices.isEmpty) {
      Left("No codec defined")
    } else {
      Right(Body.fromChunk(choices.head._2.codec.encode(value), mediaType = choices.head._1))
    }
  }

  def only(mediaType: MediaType): HttpContentCodec[A] =
    HttpContentCodec(
      ListMap(
        mediaType -> lookup(mediaType)
          .getOrElse(
            throw new IllegalArgumentException(s"MediaType $mediaType is not supported by $self"),
          ),
      ),
    )

  def only(mediaType: Option[MediaType]): HttpContentCodec[A] = {
    mediaType match {
      case Some(mediaType) =>
        HttpContentCodec(
          ListMap(
            mediaType -> lookup(mediaType)
              .getOrElse(
                throw new IllegalArgumentException(s"MediaType $mediaType is not supported by $self"),
              ),
          ),
        )
      case None            =>
        self
    }
  }

  private[http] def chooseFirst(mediaTypes: Chunk[MediaTypeWithQFactor]): (MediaType, BinaryCodecWithSchema[A]) =
    if (mediaTypes.isEmpty) {
      (defaultMediaType, defaultBinaryCodecWithSchema)
    } else {
      var i                                             = 0
      var result: (MediaType, BinaryCodecWithSchema[A]) = null
      while (i < mediaTypes.size && result == null) {
        val mediaType    = mediaTypes(i)
        val lookupResult = lookup(mediaType.mediaType)
        if (lookupResult.isDefined) result = (mediaType.mediaType, lookupResult.get)
        i += 1
      }
      if (result == null) {
        throw new IllegalArgumentException(s"None of the media types $mediaTypes are supported by $self")
      } else {
        result
      }
    }

  private[http] def chooseFirstOrDefault(
    mediaTypes: Chunk[MediaTypeWithQFactor],
  ): (MediaType, BinaryCodecWithSchema[A]) =
    if (mediaTypes.isEmpty) {
      (defaultMediaType, defaultBinaryCodecWithSchema)
    } else {
      var i                                             = 0
      var result: (MediaType, BinaryCodecWithSchema[A]) = null
      while (i < mediaTypes.size && result == null) {
        val mediaType    = mediaTypes(i)
        val lookupResult = lookup(mediaType.mediaType)
        if (lookupResult.isDefined) result = (mediaType.mediaType, lookupResult.get)
        i += 1
      }
      if (result == null)
        (defaultMediaType, defaultBinaryCodecWithSchema)
      else {
        result
      }
    }

  def lookup(mediaType: MediaType): Option[BinaryCodecWithSchema[A]] = {
    if (lookupCache.contains(mediaType)) {
      lookupCache(mediaType)
    } else {
      val codec = choices.collectFirst { case (mt, codec) if mt.matches(mediaType) => codec }
      lookupCache = lookupCache + (mediaType -> codec)
      codec
    }
  }

  private[http] val defaultMediaType: MediaType =
    choices.headOption.map(_._1).getOrElse {
      throw new IllegalArgumentException(s"No codec defined")
    }

  val defaultCodec: BinaryCodec[A] = choices.headOption.map(_._2.codec).getOrElse {
    throw new IllegalArgumentException(s"No codec defined")
  }

  val defaultSchema: Schema[A] = choices.headOption.map(_._2.schema).getOrElse {
    throw new IllegalArgumentException(s"No codec defined")
  }

  val defaultBinaryCodecWithSchema: BinaryCodecWithSchema[A] =
    choices.headOption.map(_._2).getOrElse {
      throw new IllegalArgumentException(s"No codec defined")
    }
}

object HttpContentCodec {

  private final case class DefaultCodecError(name: String, message: String)

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
                h1("Codec Error"),
                p("There was an error en-/decoding the request/response"),
                p(name, idAttr    := "name"),
                p(message, idAttr := "message"),
              ),
            ),
          )
        case e: HttpCodecError                         =>
          Right(
            html(
              body(
                h1("Codec Error"),
                p("There was an error en-/decoding the request/response"),
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

  val responseErrorCodec: HttpCodec[HttpCodecType.ResponseType, HttpCodecError] =
    ContentCodec.content(defaultHttpContentCodec) ++ StatusCodec.BadRequest

  def from[A](
    codec: (MediaType, BinaryCodecWithSchema[A]),
    codecs: (MediaType, BinaryCodecWithSchema[A])*,
  ): HttpContentCodec[A] =
    HttpContentCodec(ListMap((codec +: codecs): _*))

  implicit def fromSchema[A](implicit schema: Schema[A]): HttpContentCodec[A] = {
    json.only[A] ++ protobuf.only[A] ++ text.only[A]
  }

  object json {
    def only[A](implicit schema: Schema[A]): HttpContentCodec[A] = {
      HttpContentCodec(
        ListMap(
          MediaType.application.`json` ->
            BinaryCodecWithSchema(
              JsonCodec.schemaBasedBinaryCodec[A](JsonCodec.Config(ignoreEmptyCollections = true)),
              schema,
            ),
        ),
      )
    }
  }

  object protobuf {
    def only[A](implicit schema: Schema[A]): HttpContentCodec[A] = {
      HttpContentCodec(
        ListMap(
          MediaType.parseCustomMediaType("application/protobuf").get ->
            BinaryCodecWithSchema(ProtobufCodec.protobufCodec[A], schema),
        ),
      )
    }
  }

  object text {
    def only[A](implicit schema: Schema[A]): HttpContentCodec[A] = {
      HttpContentCodec(
        ListMap(
          MediaType.text.`plain`               ->
            BinaryCodecWithSchema(zio.http.codec.internal.TextBinaryCodec.fromSchema[A](schema), schema),
          MediaType.application.`octet-stream` ->
            BinaryCodecWithSchema(zio.http.codec.internal.TextBinaryCodec.fromSchema[A](schema), schema),
        ),
      )
    }
  }

  private val ByteChunkBinaryCodec: BinaryCodec[Chunk[Byte]] = new BinaryCodec[Chunk[Byte]] {

    override def encode(value: Chunk[Byte]): Chunk[Byte] = value

    override def decode(bytes: Chunk[Byte]): Either[DecodeError, Chunk[Byte]] =
      Right(bytes)

    override def streamDecoder: ZPipeline[Any, DecodeError, Byte, Chunk[Byte]] =
      ZPipeline.identity[Byte].chunks

    override def streamEncoder: ZPipeline[Any, Nothing, Chunk[Byte], Byte] =
      ZPipeline.identity[Chunk[Byte]].flattenChunks
  }

  implicit val byteChunkCodec: HttpContentCodec[Chunk[Byte]] = {
    HttpContentCodec(
      ListMap(
        MediaType.allMediaTypes
          .filter(_.binary)
          .map(mt => mt -> BinaryCodecWithSchema(ByteChunkBinaryCodec, Schema.chunk[Byte])): _*,
      ),
    )
  }

  private val ByteBinaryCodec: BinaryCodec[Byte] = new BinaryCodec[Byte] {
    override def encode(value: Byte): Chunk[Byte] = Chunk.single(value)

    override def decode(bytes: Chunk[Byte]): Either[DecodeError, Byte] =
      if (bytes.size == 1) Right(bytes.head)
      else Left(DecodeError.ReadError(Cause.empty, "Expected a single byte"))

    override def streamDecoder: ZPipeline[Any, DecodeError, Byte, Byte] =
      ZPipeline.identity[Byte]

    override def streamEncoder: ZPipeline[Any, Nothing, Byte, Byte] =
      ZPipeline.identity[Byte]
  }

  implicit val byteCodec: HttpContentCodec[Byte] = {
    HttpContentCodec(
      ListMap(
        MediaType.allMediaTypes
          .filter(_.binary)
          .map(mt => mt -> BinaryCodecWithSchema(ByteBinaryCodec, Schema[Byte])): _*,
      ),
    )
  }
}
