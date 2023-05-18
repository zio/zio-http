package zio.http.codec.internal

import zio._
import zio.http._
import zio.schema.codec._
import zio.schema.{Schema, StandardType}
import zio.stream.ZPipeline

import java.time.{
  DayOfWeek,
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  Month,
  MonthDay,
  OffsetTime,
  Period,
  Year,
  YearMonth,
  ZoneId,
  ZoneOffset,
}
import java.util.UUID
import scala.util.Try

sealed trait MediaTypeCodec[Codec] {

  def acceptedTypes: Chunk[MediaType]
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
    content: Chunk[BodyCodec[_]],
    binaryCodec: Schema[Any] => BinaryCodec[Any],
    acceptTypes: Chunk[MediaType],
  ): BinaryMediaTypeCodec = new BinaryMediaTypeCodec {

    override def encoders: Chunk[Any => Body] =
      content.map { bodyCodec =>
        val erased = bodyCodec.erase
        val codec  = binaryCodec(erased.schema.asInstanceOf[Schema[Any]]).asInstanceOf[BinaryCodec[erased.Element]]
        erased.encodeToBody(_, codec)
      }

    override def decoders: Chunk[Body => IO[Throwable, _]] =
      content.map { bodyCodec =>
        val codec =
          binaryCodec(bodyCodec.schema.asInstanceOf[Schema[Any]])
            .asInstanceOf[BinaryCodec[bodyCodec.Element]]
        bodyCodec.decodeFromBody(_, codec)
      }

    override def codecs: Map[BodyCodec[Any], BinaryCodec[Any]] =
      content.map { bodyCodec =>
        val codec =
          binaryCodec(bodyCodec.schema.asInstanceOf[Schema[Any]])
            .asInstanceOf[BinaryCodec[bodyCodec.Element]]
        bodyCodec.erase -> codec.asInstanceOf[BinaryCodec[Any]]
      }.toMap

    override val acceptedTypes: Chunk[MediaType] = acceptTypes
  }

  private def text(
    content: Chunk[BodyCodec[_]],
    textCodec: Schema[Any] => Codec[String, Char, Any],
    acceptTypes: Chunk[MediaType],
  ): TextMediaTypeCodec = new TextMediaTypeCodec {

    override def encoders: Chunk[Any => Body] =
      content.map { bodyCodec =>
        val erased = bodyCodec.erase
        val codec = textCodec(erased.schema.asInstanceOf[Schema[Any]]).asInstanceOf[Codec[String, Char, erased.Element]]
        ((a: erased.Element) => erased.encodeToBody(a, codec)).asInstanceOf[Any => Body]
      }

    override def decoders: Chunk[Body => IO[Throwable, _]] =
      content.map { bodyCodec =>
        val codec =
          textCodec(bodyCodec.schema.asInstanceOf[Schema[Any]]).asInstanceOf[Codec[String, Char, bodyCodec.Element]]
        bodyCodec.decodeFromBody(_, codec)
      }

    override def codecs: Map[BodyCodec[Any], Codec[String, Char, Any]] =
      content.map { bodyCodec =>
        val codec =
          textCodec(bodyCodec.schema.asInstanceOf[Schema[Any]])
            .asInstanceOf[Codec[String, Char, bodyCodec.Element]]
        bodyCodec.erase -> codec.asInstanceOf[Codec[String, Char, Any]]
      }.toMap

    override val acceptedTypes: Chunk[MediaType] = acceptTypes
  }

  def json(content: Chunk[BodyCodec[_]]): BinaryMediaTypeCodec =
    binary(
      content,
      JsonCodec.schemaBasedBinaryCodec[Any](_),
      Chunk(
        MediaType.application.`json`,
      ),
    )

  def protobuf(content: Chunk[BodyCodec[_]]): BinaryMediaTypeCodec =
    binary(
      content,
      ProtobufCodec.protobufCodec[Any](_),
      Chunk(
        MediaType.parseCustomMediaType("application/protobuf").get,
      ),
    )

  def text(content: Chunk[BodyCodec[_]]): TextMediaTypeCodec =
    text(
      content,
      TextCodec.fromSchema[Any],
      Chunk.fromIterable(MediaType.text.all),
    )

}

private[internal] object TextCodec {
  def fromSchema[A](schema: Schema[A]): Codec[String, Char, A] = {
    if (!schema.isInstanceOf[Schema.Primitive[_]]) {
      throw new IllegalArgumentException(
        s"Schema $schema is not a primitive. Only primitive schemas are supported by TextCodec.",
      )
    }

    new Codec[String, Char, A] {
      override def encode(a: A): String                      =
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
              case StandardType.StringType => Right(s)
              case StandardType.BoolType   => Try(s.toBoolean).toEither
              case StandardType.ByteType   => Try(s.toByte).toEither
              case StandardType.ShortType  => Try(s.toShort).toEither
              case StandardType.IntType    => Try(s.toInt).toEither
              case StandardType.LongType   => Try(s.toLong).toEither
              case StandardType.FloatType  => Try(s.toFloat).toEither
              case StandardType.DoubleType => Try(s.toDouble).toEither
              case StandardType.BinaryType => Left(DecodeError.ValidationError(null, null, "Binary is not supported"))
              case StandardType.CharType   => Right(s.charAt(0))
              case StandardType.UUIDType   => Try(UUID.fromString(s)).toEither
              case StandardType.BigDecimalType    => Try(BigDecimal(s)).toEither
              case StandardType.BigIntegerType    => Try(BigInt(s)).toEither
              case StandardType.DayOfWeekType     => Try(DayOfWeek.valueOf(s)).toEither
              case StandardType.MonthType         => Try(Month.valueOf(s)).toEither
              case StandardType.MonthDayType      => Try(MonthDay.parse(s)).toEither
              case StandardType.PeriodType        => Try(Period.parse(s)).toEither
              case StandardType.YearType          => Try(Year.parse(s)).toEither
              case StandardType.YearMonthType     => Try(YearMonth.parse(s)).toEither
              case StandardType.ZoneIdType        => Try(ZoneId.of(s)).toEither
              case StandardType.ZoneOffsetType    => Try(ZoneOffset.of(s)).toEither
              case StandardType.DurationType      => Try(java.time.Duration.parse(s)).toEither
              case StandardType.InstantType       => Try(Instant.parse(s)).toEither
              case StandardType.LocalDateType     => Try(LocalDate.parse(s)).toEither
              case StandardType.LocalTimeType     => Try(LocalTime.parse(s)).toEither
              case StandardType.LocalDateTimeType => Try(LocalDateTime.parse(s)).toEither
              case StandardType.OffsetTimeType    => Try(OffsetTime.parse(s)).toEither
            }).map(_.asInstanceOf[A]).left.map(e => DecodeError.ReadError(Cause.fail(e), e.getMessage))
          case _                                 =>
            Left(
              DecodeError.ReadError(Cause.empty, "Only primitive types are supported. But found: " + schema.toString),
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
