package zio.http.codec

import scala.collection.immutable.ListMap

import zio._

import zio.stream.ZPipeline

import zio.schema.Schema
import zio.schema.codec._

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.internal.HeaderOps

final case class HttpContentCodec[A](
  choices: ListMap[MediaType, BinaryCodec[A]],
  schema: Schema[A],
) { self =>

  private var lookupCache: Map[MediaType, Option[BinaryCodec[A]]] = Map.empty

  /**
   * A right biased merge of two HttpContentCodecs.
   */
  def ++(that: HttpContentCodec[A]): HttpContentCodec[A] =
    HttpContentCodec(choices ++ that.choices, schema)

  def decodeRequest(request: Request): Task[A] = {
    val contentType = mediaTypeFromContentTypeHeader(request)
    lookup(contentType) match {
      case Some(codec) =>
        request.body.asChunk.flatMap { bytes =>
          ZIO.fromEither(codec.decode(bytes))
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
          ZIO.fromEither(codec.decode(bytes))
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
      Right(Body.fromChunk(choices.head._2.encode(value), mediaType = choices.head._1))
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
      schema,
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
          schema,
        )
      case None            =>
        self
    }
  }

  private[http] def chooseFirst(mediaTypes: Chunk[MediaTypeWithQFactor]): (MediaType, BinaryCodec[A]) =
    if (mediaTypes.isEmpty) {
      (defaultMediaType, defaultCodec)
    } else {
      var i                                   = 0
      var result: (MediaType, BinaryCodec[A]) = null
      while (i < mediaTypes.size) {
        val mediaType = mediaTypes(i)
        if (choices.contains(mediaType.mediaType)) {
          result = (mediaType.mediaType, choices(mediaType.mediaType))
          i = mediaTypes.size
        }
        i += 1
      }
      if (result == null) {
        throw new IllegalArgumentException(s"None of the media types $mediaTypes are supported by $self")
      } else {
        result
      }
    }

  def lookup(mediaType: MediaType): Option[BinaryCodec[A]] = {
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

  val defaultCodec: BinaryCodec[A] = choices.headOption.map(_._2).getOrElse {
    throw new IllegalArgumentException(s"No codec defined")
  }
}

object HttpContentCodec {
  implicit def fromSchema[A](implicit schema: Schema[A]): HttpContentCodec[A] = {
    json.only[A] ++ protobuf.only[A] ++ text.only[A]
  }

  object json {
    def only[A](implicit schema: Schema[A]): HttpContentCodec[A] = {
      HttpContentCodec(
        ListMap(
          MediaType.application.`json` ->
            JsonCodec.schemaBasedBinaryCodec[A](JsonCodec.Config(ignoreEmptyCollections = true)),
        ),
        schema,
      )
    }
  }

  object protobuf {
    def only[A](implicit schema: Schema[A]): HttpContentCodec[A] = {
      HttpContentCodec(
        ListMap(
          MediaType.parseCustomMediaType("application/protobuf").get ->
            ProtobufCodec.protobufCodec[A],
        ),
        schema,
      )
    }
  }

  object text {
    def only[A](implicit schema: Schema[A]): HttpContentCodec[A] = {
      HttpContentCodec(
        ListMap(
          MediaType.text.`plain` ->
            zio.http.codec.internal.TextCodec.fromSchema[A](schema),
        ),
        schema,
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

  val byteChunkCodec: HttpContentCodec[Chunk[Byte]] = {
    HttpContentCodec(
      ListMap(
        MediaType.allMediaTypes.filter(_.binary).map(mt => mt -> ByteChunkBinaryCodec): _*,
      ),
      Schema.chunk[Byte],
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

  val byteCodec: HttpContentCodec[Byte] = {
    HttpContentCodec(
      ListMap(
        MediaType.allMediaTypes.filter(_.binary).map(mt => mt -> ByteBinaryCodec): _*,
      ),
      Schema[Byte],
    )
  }
}
