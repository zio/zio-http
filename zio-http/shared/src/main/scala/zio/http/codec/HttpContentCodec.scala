package zio.http.codec

import scala.collection.immutable.ListMap

import zio._

import zio.stream._

import zio.schema.codec._
import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.internal.HeaderOps
import zio.http.template._

/**
 * A codec for a body of type `A` in one or more media types. It maps each media
 * type to a [[BinaryCodecWithSchema]]. Decoding picks the codec by the
 * `Content-Type` of the message; encoding uses the first media type, unless the
 * client asks for another one with `Accept`. `HttpCodec.content[A]` finds one
 * implicitly; `HttpContentCodec.fromSchema` derives one from a `Schema`.
 *
 * {{{
 * import zio.http.codec._
 * import zio.schema._
 *
 * final case class Book(title: String)
 * implicit val bookSchema: Schema[Book] = DeriveSchema.gen[Book]
 *
 * val codec: HttpContentCodec[Book] = HttpContentCodec.fromSchema[Book]
 * val mediaTypes: List[String]      = codec.choices.keys.map(_.fullType).toList
 * // List(application/json, application/protobuf, text/plain, application/octet-stream)
 * }}}
 */
sealed trait HttpContentCodec[A] { self =>

  /**
   * The media types this codec supports, in order of preference, each with its
   * codec.
   */
  def choices: ListMap[MediaType, BinaryCodecWithSchema[A]]

  /**
   * A right-biased merge of two HttpContentCodecs. The media types of this
   * codec come first; for a media type that both codecs support, the codec of
   * `that` is used.
   */
  def ++(that: HttpContentCodec[A]): HttpContentCodec[A] =
    HttpContentCodec.Choices(choices ++ that.choices)

  /**
   * Decodes the body of `request` with the codec for its `Content-Type`, or for
   * `application/json` when the request has no `Content-Type`. It fails with an
   * `IllegalArgumentException` when no media type of this codec matches, and
   * with the `DecodeError` of the codec when the body cannot be decoded.
   */
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

  /**
   * Decodes the body of `request` like the overload with a config, using the
   * [[CodecConfig]] of the current fiber.
   */
  def decodeRequest(request: Request): Task[A] =
    CodecConfig.codecRef.getWith(decodeRequest(request, _))

  /**
   * Decodes the body of `response` with the codec for its `Content-Type`, or
   * for `application/json` when the response has no `Content-Type`. It fails
   * with an `IllegalArgumentException` when no media type of this codec
   * matches, and with the `DecodeError` of the codec when the body cannot be
   * decoded.
   */
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

  /**
   * Decodes the body of `response` like the overload with a config, using the
   * [[CodecConfig]] of the current fiber.
   */
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

  /**
   * Encodes `value` with the codec of the first media type, into a body with
   * that media type.
   */
  def encode(value: A, config: CodecConfig = CodecConfig.defaultConfig): Either[String, Body] = {
    if (choices.isEmpty) {
      Left("No codec defined")
    } else {
      Right(Body.fromChunk(choices.head._2.codec(config).encode(value), mediaType = choices.head._1))
    }
  }

  /**
   * Returns a codec that supports only `mediaType`. It throws an
   * `IllegalArgumentException` when this codec does not support `mediaType`.
   */
  def only(mediaType: MediaType): HttpContentCodec[A] =
    if (lookup(mediaType).isEmpty) {
      throw new IllegalArgumentException(s"MediaType $mediaType is not supported by $self")
    } else {
      HttpContentCodec.Filtered(self, mediaType)
    }

  /**
   * Returns a codec that supports only the given media type, or this codec when
   * `mediaType` is `None`.
   */
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

  /**
   * Returns the first media type of this codec that matches `mediaType`, with
   * its codec, or `None` when no media type matches. `MediaType.any` matches
   * the first media type.
   */
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

  /**
   * Returns a codec of `Option[A]` that decodes an empty body as `None` and
   * encodes `None` as an empty body.
   */
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
      ZPipeline.chunks[Byte].map(bc.decode(_).toOption)

    override def streamEncoder: ZPipeline[Any, Nothing, Option[A], Byte] =
      ZPipeline.identity[Option[A]].map(_.fold(Chunk.empty[Byte])(bc.encode)).flattenChunks
  }

}

/** Constructors and predefined instances of [[HttpContentCodec]]. */
object HttpContentCodec {

  /**
   * An [[HttpContentCodec]] with the given media types and codecs, in order.
   */
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

  /** The media type `mediaType` of `codec`, alone; the result of `only`. */
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

  /**
   * The codec of an [[HttpCodecError]] response: status `400 Bad Request`, with
   * a body that holds the name and the message of the error. The body is HTML
   * (`text/html`) by default, or JSON (`application/json`) on request.
   */
  val responseErrorCodec: HttpCodec[HttpCodecType.ResponseType, HttpCodecError] =
    ContentCodec.content(defaultHttpContentCodec) ++ StatusCodec.BadRequest

  private var fromSchemaCache: Map[Schema[_], HttpContentCodec[_]] = Map.empty

  /**
   * Creates a codec with the given media types and codecs, in order. It throws
   * an `IllegalArgumentException` when `choices` is empty.
   */
  def apply[A](choices: ListMap[MediaType, BinaryCodecWithSchema[A]]): HttpContentCodec[A] =
    Choices(choices)

  /** Creates a codec with the given media types and codecs, in order. */
  def from[A](
    codec: (MediaType, BinaryCodecWithSchema[A]),
    codecs: (MediaType, BinaryCodecWithSchema[A])*,
  ): HttpContentCodec[A] =
    HttpContentCodec.Choices(ListMap((codec +: codecs): _*))

  /**
   * Derives a codec from `schema` with the media types `application/json`,
   * `application/protobuf`, `text/plain` and `application/octet-stream`, in
   * this order. The last two use [[TextBinaryCodec]]. For an `Option` schema,
   * an empty body decodes as `None`. The result is cached per schema.
   */
  implicit def fromSchema[A](implicit schema: Schema[A]): HttpContentCodec[A] = {
    if (fromSchemaCache.contains(schema)) {
      fromSchemaCache(schema).asInstanceOf[HttpContentCodec[A]]
    } else {
      val codec: HttpContentCodec[A] = schema match {
        // Special handling for Option types: create codec for inner type and call .optional
        // This ensures empty body is properly decoded as None
        case Schema.Optional(innerSchema, _) =>
          val innerCodec = json.only(innerSchema) ++ protobuf.only(innerSchema) ++ text.only(innerSchema)
          innerCodec.optional.asInstanceOf[HttpContentCodec[A]]
        case _                               =>
          json.only[A] ++ protobuf.only[A] ++ text.only[A]
      }
      fromSchemaCache = fromSchemaCache + (schema -> codec)
      codec
    }

  }

  /** Codecs for `application/json` only. */
  object json {

    private var jsonCodecCache: Map[Schema[_], HttpContentCodec[_]] = Map.empty

    /**
     * Returns a codec for `application/json` only, derived from `schema` and
     * configured by [[CodecConfig]].
     */
    def only[A](implicit schema: Schema[A]): HttpContentCodec[A] =
      if (jsonCodecCache.contains(schema)) {
        jsonCodecCache(schema).asInstanceOf[HttpContentCodec[A]]
      } else {
        val codec = HttpContentCodec.Choices(
          ListMap(
            MediaType.application.`json` ->
              BinaryCodecWithSchema(
                config => {
                  JsonCodec.schemaBasedBinaryCodec(config.schemaConfig)(schema)
                },
                schema,
              ),
          ),
        )
        jsonCodecCache = jsonCodecCache + (schema -> codec)
        codec
      }
  }

  /** Codecs for `application/protobuf` only. */
  object protobuf {

    private var protobufCodecCache: Map[Schema[_], HttpContentCodec[_]] = Map.empty

    /**
     * Returns a codec for `application/protobuf` only, derived from `schema`.
     */
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

  /** Codecs for `text/plain` and `application/octet-stream`. */
  object text {

    private var textCodecCache: Map[Schema[_], HttpContentCodec[_]] = Map.empty

    /**
     * Returns a codec for `text/plain` and `application/octet-stream` that
     * writes the value as text with [[TextBinaryCodec]]. It supports the
     * schemas that `TextBinaryCodec` supports, such as primitives; other
     * schemas throw an `IllegalArgumentException` when used.
     */
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

  /** Codecs for `application/x-www-form-urlencoded` only. */
  object form {

    private var formCodecCache: Map[Schema[_], HttpContentCodec[_]] = Map.empty

    /**
     * Returns a codec for `application/x-www-form-urlencoded` only, which
     * writes the fields of `A` as form fields, for example `name=a+b&age=1`.
     */
    def only[A](implicit schema: Schema[A]): HttpContentCodec[A] =
      if (formCodecCache.contains(schema)) {
        formCodecCache(schema).asInstanceOf[HttpContentCodec[A]]
      } else {
        val codec = HttpContentCodec.Choices(
          ListMap(
            MediaType.application.`x-www-form-urlencoded` ->
              BinaryCodecWithSchema(formBinaryCodec[A](schema), schema),
          ),
        )
        formCodecCache = formCodecCache + (schema -> codec)
        codec
      }

    private def formBinaryCodec[A](implicit schema: Schema[A]): BinaryCodec[A] = new BinaryCodec[A] {
      private lazy val queryCodec =
        zio.http.internal.StringSchemaCodec.queryFromSchema[A](schema, zio.http.internal.ErrorConstructor.query, null)

      override def encode(value: A): Chunk[Byte] = {
        val queryParams = queryCodec.encode(value, QueryParams.empty)
        val encoded     = queryParams.encode.drop(1) // drop leading '?'
        Chunk.fromArray(encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      }

      override def decode(bytes: Chunk[Byte]): Either[DecodeError, A] = {
        val str         = new String(bytes.toArray, java.nio.charset.StandardCharsets.UTF_8)
        val queryParams = QueryParams.decode(str)
        try Right(queryCodec.decode(queryParams))
        catch {
          case e: HttpCodecError => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
          case e: Exception      => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
        }
      }

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.chunks[Byte].mapZIO(bytes => ZIO.fromEither(decode(bytes)))

      override def streamEncoder: ZPipeline[Any, Nothing, A, Byte] =
        ZPipeline.identity[A].map(encode).flattenChunks
    }
  }

  private[http] implicit val ByteChunkBinaryCodec: BinaryCodec[Chunk[Byte]] = new BinaryCodec[Chunk[Byte]] {

    override def encode(value: Chunk[Byte]): Chunk[Byte] = value

    override def decode(bytes: Chunk[Byte]): Either[DecodeError, Chunk[Byte]] =
      Right(bytes)

    override def streamDecoder: ZPipeline[Any, DecodeError, Byte, Chunk[Byte]] =
      ZPipeline.identity[Byte].chunks

    override def streamEncoder: ZPipeline[Any, Nothing, Chunk[Byte], Byte] =
      ZPipeline.identity[Chunk[Byte]].flattenChunks
  }

  /**
   * The codec of raw bytes, as a `Chunk[Byte]`, for every binary media type
   * known to `MediaType`. Its first media type is `application/x-bdoc`.
   */
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

  /**
   * The codec of single bytes for every binary media type known to `MediaType`,
   * used by byte streams such as `HttpCodec.binaryStream`. Its first media type
   * is `application/x-bdoc`. Decoding a whole body with it requires exactly one
   * byte.
   */
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
