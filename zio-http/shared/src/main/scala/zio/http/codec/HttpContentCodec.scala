package zio.http.codec

import java.nio.charset.StandardCharsets

import scala.collection.immutable.ListMap

import zio._

import zio.stream.{ZChannel, ZPipeline}

import zio.schema.codec.DecodeError.ReadError
import zio.schema.codec.JsonCodec.{JsonDecoder, JsonEncoder}
import zio.schema.codec._
import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.internal.HeaderOps
import zio.http.template._

sealed trait HttpContentCodec[A] { self =>
  def choices: ListMap[MediaType, BinaryCodecWithSchema[A]]

  /**
   * A right biased merge of two HttpContentCodecs.
   */
  def ++(that: HttpContentCodec[A]): HttpContentCodec[A] =
    HttpContentCodec.Choices(choices ++ that.choices)

  def decodeRequest(request: Request, config: CodecConfig): Task[A] = {
    val contentType = mediaTypeFromContentTypeHeader(request)
    lookup(contentType) match {
      case Some((_, codec)) =>
        request.body.asChunk.flatMap { bytes =>
          ZIO.fromEither(codec.codec(config).decode(bytes))
        }
      case None             =>
        ZIO.fail(new IllegalArgumentException(s"No codec found for content type $contentType"))
    }
  }

  def decodeRequest(request: Request): Task[A] =
    CodecConfig.codecRef.getWith(decodeRequest(request, _))

  def decodeResponse(response: Response, config: CodecConfig): Task[A] = {
    val contentType = mediaTypeFromContentTypeHeader(response)
    lookup(contentType) match {
      case Some((_, codec)) =>
        response.body.asChunk.flatMap { bytes =>
          ZIO.fromEither(codec.codec(config).decode(bytes))
        }
      case None             =>
        ZIO.fail(new IllegalArgumentException(s"No codec found for content type $contentType"))
    }
  }

  def decodeResponse(response: Response): Task[A] =
    CodecConfig.codecRef.getWith(decodeResponse(response, _))

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

  def encode(value: A, config: CodecConfig = CodecConfig.defaultConfig): Either[String, Body] = {
    if (choices.isEmpty) {
      Left("No codec defined")
    } else {
      Right(Body.fromChunk(choices.head._2.codec(config).encode(value), mediaType = choices.head._1))
    }
  }

  def only(mediaType: MediaType): HttpContentCodec[A] =
    if (lookup(mediaType).isEmpty) {
      throw new IllegalArgumentException(s"MediaType $mediaType is not supported by $self")
    } else {
      HttpContentCodec.Filtered(self, mediaType)
    }

  def only(mediaType: Option[MediaType]): HttpContentCodec[A] =
    mediaType match {
      case Some(value) => only(value)
      case None        => self
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
        if (lookupResult.isDefined) result = lookupResult.get
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
        if (lookupResult.isDefined) result = lookupResult.get
        i += 1
      }
      if (result == null) (defaultMediaType, defaultBinaryCodecWithSchema)
      else result
    }

  def lookup(mediaType: MediaType): Option[(MediaType, BinaryCodecWithSchema[A])]

  private[http] val defaultMediaType: MediaType =
    choices.headOption.map(_._1).getOrElse {
      throw new IllegalArgumentException(s"No codec defined")
    }

  private[http] val defaultCodec: BinaryCodec[A] =
    choices.headOption.map(_._2.codec(CodecConfig.defaultConfig)).getOrElse {
      throw new IllegalArgumentException(s"No codec defined")
    }

  private[http] val defaultSchema: Schema[A] = choices.headOption.map(_._2.schema).getOrElse {
    throw new IllegalArgumentException(s"No codec defined")
  }

  private[http] val defaultBinaryCodecWithSchema: BinaryCodecWithSchema[A] =
    choices.headOption.map(_._2).getOrElse {
      throw new IllegalArgumentException(s"No codec defined")
    }

  def optional: HttpContentCodec[Option[A]] =
    self match {
      case HttpContentCodec.Choices(choices)           =>
        HttpContentCodec.Choices(
          choices.map { case (mediaType, BinaryCodecWithSchema(fromConfig, schema)) =>
            mediaType -> BinaryCodecWithSchema(fromConfig.andThen(optBinaryCodec), schema.optional)
          },
        )
      case HttpContentCodec.Filtered(codec, mediaType) =>
        HttpContentCodec.Filtered(codec.optional, mediaType)
    }

  private def optBinaryCodec(bc: BinaryCodec[A]): BinaryCodec[Option[A]] = new BinaryCodec[Option[A]] {
    override def encode(value: Option[A]): Chunk[Byte] = value match {
      case Some(a) => bc.encode(a)
      case None    => Chunk.empty
    }

    override def decode(bytes: Chunk[Byte]): Either[DecodeError, Option[A]] =
      if (bytes.isEmpty) Right(None)
      else bc.decode(bytes).map(Some(_))

    override def streamDecoder: ZPipeline[Any, DecodeError, Byte, Option[A]] =
      ZPipeline.chunks[Byte].map(bc.decode).map(_.toOption)

    override def streamEncoder: ZPipeline[Any, Nothing, Option[A], Byte] =
      ZPipeline.identity[Option[A]].map(_.fold(Chunk.empty[Byte])(bc.encode)).flattenChunks
  }

}

object HttpContentCodec {
  final case class Choices[A](
    choices: ListMap[MediaType, BinaryCodecWithSchema[A]],
  ) extends HttpContentCodec[A] {
    private var lookupCache: Map[MediaType, Option[(MediaType, BinaryCodecWithSchema[A])]] = Map.empty

    override def lookup(mediaType: MediaType): Option[(MediaType, BinaryCodecWithSchema[A])] = {
      if (lookupCache.contains(mediaType)) {
        lookupCache(mediaType)
      } else {
        val codec = choices.collectFirst { case (mt, codec) if mt.matches(mediaType) => mt -> codec }
        lookupCache = lookupCache + (mediaType -> codec)
        codec
      }
    }
  }

  final case class Filtered[A](codec: HttpContentCodec[A], mediaType: MediaType) extends HttpContentCodec[A] {
    self =>
    override lazy val choices: ListMap[MediaType, BinaryCodecWithSchema[A]] =
      codec.choices.filter(_._1 == mediaType)

    private val choice = choices.headOption

    override def lookup(mediaType: MediaType): Option[(MediaType, BinaryCodecWithSchema[A])] =
      if (self.mediaType.matches(mediaType)) choice else None
  }

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

  private var fromSchemaCache: Map[Schema[_], HttpContentCodec[_]] = Map.empty

  def apply[A](choices: ListMap[MediaType, BinaryCodecWithSchema[A]]): HttpContentCodec[A] =
    Choices(choices)

  def from[A](
    codec: (MediaType, BinaryCodecWithSchema[A]),
    codecs: (MediaType, BinaryCodecWithSchema[A])*,
  ): HttpContentCodec[A] =
    HttpContentCodec.Choices(ListMap((codec +: codecs): _*))

  implicit def fromSchema[A](implicit schema: Schema[A]): HttpContentCodec[A] = {
    if (fromSchemaCache.contains(schema)) {
      fromSchemaCache(schema).asInstanceOf[HttpContentCodec[A]]
    } else {
      val codec = json.only[A] ++ protobuf.only[A] ++ text.only[A]
      fromSchemaCache = fromSchemaCache + (schema -> codec)
      codec
    }

  }

  object json {

    private var jsonCodecCache: Map[Schema[_], HttpContentCodec[_]] = Map.empty
    def only[A](implicit schema: Schema[A]): HttpContentCodec[A]    =
      if (jsonCodecCache.contains(schema)) {
        jsonCodecCache(schema).asInstanceOf[HttpContentCodec[A]]
      } else {
        val codec = HttpContentCodec.Choices(
          ListMap(
            MediaType.application.`json` ->
              BinaryCodecWithSchema(
                config => {
                  JsonCodec.schemaBasedBinaryCodec(
                    JsonCodec
                      .Config(ignoreEmptyCollections = config.ignoreEmptyCollections, treatStreamsAsArrays = true),
                  )(schema)
                },
                schema,
              ),
          ),
        )
        jsonCodecCache = jsonCodecCache + (schema -> codec)
        codec
      }
  }

  object protobuf {

    private var protobufCodecCache: Map[Schema[_], HttpContentCodec[_]] = Map.empty

    def only[A](implicit schema: Schema[A]): HttpContentCodec[A] =
      if (protobufCodecCache.contains(schema)) {
        protobufCodecCache(schema).asInstanceOf[HttpContentCodec[A]]
      } else {
        val codec = HttpContentCodec.Choices(
          ListMap(
            MediaType.parseCustomMediaType("application/protobuf").get ->
              BinaryCodecWithSchema(ProtobufCodec.protobufCodec[A], schema),
          ),
        )
        protobufCodecCache = protobufCodecCache + (schema -> codec)
        codec
      }
  }

  object text {

    private var textCodecCache: Map[Schema[_], HttpContentCodec[_]] = Map.empty

    def only[A](implicit schema: Schema[A]): HttpContentCodec[A] =
      if (textCodecCache.contains(schema)) {
        textCodecCache(schema).asInstanceOf[HttpContentCodec[A]]
      } else {
        val codec = HttpContentCodec.Choices(
          ListMap(
            MediaType.text.`plain`               ->
              BinaryCodecWithSchema(http.codec.TextBinaryCodec.fromSchema[A](schema), schema),
            MediaType.application.`octet-stream` ->
              BinaryCodecWithSchema(http.codec.TextBinaryCodec.fromSchema[A](schema), schema),
          ),
        )
        textCodecCache = textCodecCache + (schema -> codec)
        codec
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
    HttpContentCodec.Choices(
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
    HttpContentCodec.Choices(
      ListMap(
        MediaType.allMediaTypes
          .filter(_.binary)
          .map(mt => mt -> BinaryCodecWithSchema(ByteBinaryCodec, Schema[Byte])): _*,
      ),
    )
  }
}
