package zio.http.codec.internal

import java.time._
import java.util.UUID

import scala.util.Try

import zio._

import zio.stream.ZPipeline

import zio.schema.codec._
import zio.schema.{Schema, StandardType}

import zio.http._
import zio.http.codec.HttpCodecError

final case class MediaTypeCodecDefinition[T <: MediaTypeCodec[_]](
  acceptedTypes: Chunk[MediaType],
  create: Chunk[BodyCodec[_]] => T,
)

sealed trait MediaTypeCodec[Codec] {
  def decoders: Chunk[Body => IO[Throwable, _]]
  def decodeSingle(body: Body): IO[Throwable, Any] = decoders(0)(body)
  def encoders: Chunk[Any => Body]
  def encodeSingle(a: Any): Body                   = encoders(0)(a)
  def codecs: Map[BodyCodec[Any], Codec]

}

sealed trait BinaryMediaTypeCodec extends MediaTypeCodec[BinaryCodec[Any]]

sealed trait TextMediaTypeCodec extends MediaTypeCodec[Codec[String, Char, Any]]

object MediaTypeCodec {
  private def binary(
    binaryCodec: Schema[Any] => BinaryCodec[Any],
    acceptTypes: Chunk[MediaType],
  ): MediaTypeCodecDefinition[BinaryMediaTypeCodec] =
    MediaTypeCodecDefinition(
      acceptTypes,
      { (content: Chunk[BodyCodec[_]]) =>
        new BinaryMediaTypeCodec {

          lazy val encoders: Chunk[Any => Body] =
            content.map { (bodyCodec: BodyCodec[_]) =>
              val erased = bodyCodec.erase
              val codec = binaryCodec(erased.schema.asInstanceOf[Schema[Any]]).asInstanceOf[BinaryCodec[erased.Element]]
              erased.encodeToBody(_: Any, codec)
            }

          lazy val decoders: Chunk[Body => IO[Throwable, _]] =
            content.map { (bodyCodec: BodyCodec[_]) =>
              val codec =
                binaryCodec(bodyCodec.schema.asInstanceOf[Schema[Any]])
                  .asInstanceOf[BinaryCodec[bodyCodec.Element]]
              bodyCodec.decodeFromBody(_: Body, codec)
            }

          lazy val codecs: Map[BodyCodec[Any], BinaryCodec[Any]] =
            content.map { (bodyCodec: BodyCodec[_]) =>
              val codec =
                binaryCodec(bodyCodec.schema.asInstanceOf[Schema[Any]])
                  .asInstanceOf[BinaryCodec[bodyCodec.Element]]
              bodyCodec.erase -> codec.asInstanceOf[BinaryCodec[Any]]
            }.toMap

        }
      },
    )

  private def text(
    textCodec: Schema[Any] => Codec[String, Char, Any],
    acceptTypes: Chunk[MediaType],
  ): MediaTypeCodecDefinition[TextMediaTypeCodec] =
    MediaTypeCodecDefinition(
      acceptTypes,
      { (content: Chunk[BodyCodec[_]]) =>
        new TextMediaTypeCodec {

          lazy val encoders: Chunk[Any => Body] =
            content.map { (bodyCodec: BodyCodec[_]) =>
              val erased = bodyCodec.erase
              val codec  =
                textCodec(erased.schema.asInstanceOf[Schema[Any]]).asInstanceOf[Codec[String, Char, erased.Element]]
              ((a: erased.Element) => erased.encodeToBody(a, codec)).asInstanceOf[Any => Body]
            }

          lazy val decoders: Chunk[Body => IO[Throwable, _]] =
            content.map { (bodyCodec: BodyCodec[_]) =>
              val codec =
                textCodec(bodyCodec.schema.asInstanceOf[Schema[Any]])
                  .asInstanceOf[Codec[String, Char, bodyCodec.Element]]
              bodyCodec.decodeFromBody(_: Body, codec)
            }

          lazy val codecs: Map[BodyCodec[Any], Codec[String, Char, Any]] =
            content.map { (bodyCodec: BodyCodec[_]) =>
              val codec =
                textCodec(bodyCodec.schema.asInstanceOf[Schema[Any]])
                  .asInstanceOf[Codec[String, Char, bodyCodec.Element]]
              bodyCodec.erase -> codec.asInstanceOf[Codec[String, Char, Any]]
            }.toMap

        }
      },
    )

  private lazy val json: MediaTypeCodecDefinition[BinaryMediaTypeCodec] =
    binary(
      JsonCodec.schemaBasedBinaryCodec[Any](_),
      Chunk(
        MediaType.application.`json`,
      ),
    )

  private lazy val protobuf: MediaTypeCodecDefinition[BinaryMediaTypeCodec] =
    binary(
      ProtobufCodec.protobufCodec[Any](_),
      Chunk(
        MediaType.parseCustomMediaType("application/protobuf").get,
      ),
    )

  private lazy val text: MediaTypeCodecDefinition[TextMediaTypeCodec] =
    text(
      TextCodec.fromSchema[Any],
      Chunk.fromIterable(MediaType.text.all),
    )

  private lazy val all: Chunk[MediaTypeCodecDefinition[_ <: MediaTypeCodec[_]]] =
    Chunk(json, protobuf, text)

  private[codec] lazy val supportedMediaTypes: Chunk[String] =
    all.flatMap(_.acceptedTypes.map(_.fullType))

  private lazy val allByType: Map[String, MediaTypeCodecDefinition[_ <: MediaTypeCodec[_]]] =
    all.flatMap { codec =>
      codec.acceptedTypes.map(_.fullType).map(_ -> codec)
    }.toMap

  def codecsFor(mediaType: Option[String], content: Chunk[BodyCodec[_]]): Map[String, MediaTypeCodec[_]] = {
    mediaType match {
      case Some(mt) =>
        if (mt.contains('*')) {
          MediaType.parseCustomMediaType(mt) match {
            case Some(parsed) if parsed.mainType == "*" && parsed.subType == "*" =>
              allByType.map { case (k, v) => k -> v.create(content) }
            case Some(parsed) if parsed.subType == "*"                           =>
              allByType.filter { case (k, _) => k.startsWith(parsed.mainType + "/") }.map { case (k, v) =>
                k -> v.create(content)
              }
            case _                                                               =>
              throw HttpCodecError.UnsupportedContentType(
                s"""The Accept header mime type $mt is currently not supported.
                   |Supported mime types are: ${allByType.keys.mkString(", ")}""".stripMargin,
              )
          }
        } else {
          allByType.get(mt) match {
            case Some(codec) => Map(mt -> codec.create(content))
            case None        =>
              throw HttpCodecError.UnsupportedContentType(
                s"""The Accept header mime type $mt is currently not supported.
                   |Supported mime types are: ${allByType.keys.mkString(", ")}""".stripMargin,
              )
          }
        }
      case None     => allByType.map { case (k, v) => k -> v.create(content) }
    }
  }
}

private[internal] object TextCodec {
  def fromSchema[A](schema: Schema[A]): Codec[String, Char, A] = {
    if (!schema.isInstanceOf[Schema.Primitive[_]]) {
      new Codec[String, Char, A] {
        override def decode(whole: String): Either[DecodeError, A] = throw new IllegalArgumentException(
          s"Schema $schema is not a primitive. Only primitive schemas are supported by TextCodec.",
        )

        override def streamDecoder: ZPipeline[Any, DecodeError, Char, A] = throw new IllegalArgumentException(
          s"Schema $schema is not a primitive. Only primitive schemas are supported by TextCodec.",
        )

        override def encode(value: A): String = throw new IllegalArgumentException(
          s"Schema $schema is not a primitive. Only primitive schemas are supported by TextCodec.",
        )

        override def streamEncoder: ZPipeline[Any, Nothing, A, Char] = throw new IllegalArgumentException(
          s"Schema $schema is not a primitive. Only primitive schemas are supported by TextCodec.",
        )
      }
    } else {
      new Codec[String, Char, A] {
        override def encode(a: A): String =
          schema match {
            case Schema.Primitive(_, _) => a.toString
            case _                      =>
              throw new IllegalArgumentException(
                s"Cannot encode $a of type ${a.getClass} with schema $schema",
              )
          }

        override def decode(s: String): Either[DecodeError, A] =
          schema match {
            case Schema.Primitive(standardType, _) =>
              (standardType match {
                case StandardType.UnitType           => Right("")
                case StandardType.StringType         => Right(s)
                case StandardType.BoolType           => Try(s.toBoolean).toEither
                case StandardType.ByteType           => Try(s.toByte).toEither
                case StandardType.ShortType          => Try(s.toShort).toEither
                case StandardType.IntType            => Try(s.toInt).toEither
                case StandardType.LongType           => Try(s.toLong).toEither
                case StandardType.FloatType          => Try(s.toFloat).toEither
                case StandardType.DoubleType         => Try(s.toDouble).toEither
                // FIXME: use a proper error type (needs changes in zio-schema)
                case StandardType.BinaryType         => Left(DecodeError.EmptyContent("Binary is not supported"))
                case StandardType.CharType           => Right(s.charAt(0))
                case StandardType.UUIDType           => Try(UUID.fromString(s)).toEither
                case StandardType.BigDecimalType     => Try(BigDecimal(s)).toEither
                case StandardType.BigIntegerType     => Try(BigInt(s)).toEither
                case StandardType.DayOfWeekType      => Try(DayOfWeek.valueOf(s)).toEither
                case StandardType.MonthType          => Try(Month.valueOf(s)).toEither
                case StandardType.MonthDayType       => Try(MonthDay.parse(s)).toEither
                case StandardType.PeriodType         => Try(Period.parse(s)).toEither
                case StandardType.YearType           => Try(Year.parse(s)).toEither
                case StandardType.YearMonthType      => Try(YearMonth.parse(s)).toEither
                case StandardType.ZoneIdType         => Try(ZoneId.of(s)).toEither
                case StandardType.ZoneOffsetType     => Try(ZoneOffset.of(s)).toEither
                case StandardType.DurationType       => Try(java.time.Duration.parse(s)).toEither
                case StandardType.InstantType        => Try(Instant.parse(s)).toEither
                case StandardType.LocalDateType      => Try(LocalDate.parse(s)).toEither
                case StandardType.LocalTimeType      => Try(LocalTime.parse(s)).toEither
                case StandardType.LocalDateTimeType  => Try(LocalDateTime.parse(s)).toEither
                case StandardType.OffsetTimeType     => Try(OffsetTime.parse(s)).toEither
                case StandardType.OffsetDateTimeType => Try(OffsetDateTime.parse(s)).toEither
                case StandardType.ZonedDateTimeType  => Try(ZonedDateTime.parse(s)).toEither
              }).map(_.asInstanceOf[A]).left.map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage))
            case _                                 =>
              Left(
                // FIXME: use a proper error type (needs changes in zio-schema)
                DecodeError.EmptyContent(
                  "Only primitive types are supported for text decoding. But found: " + schema.toString,
                ),
              )
          }

        override def streamEncoder: ZPipeline[Any, Nothing, A, Char] =
          ZPipeline.map((a: A) => Chunk.fromArray(a.toString.toArray)).flattenChunks

        override def streamDecoder: ZPipeline[Any, DecodeError, Char, A] =
          (ZPipeline[Char].map(_.toByte) >>> ZPipeline.utf8Decode)
            .map(decode(_).fold(throw _, identity))
            .mapErrorCause(e => Cause.fail(DecodeError.ReadError(e, e.squash.getMessage)))
      }
    }
  }
}
