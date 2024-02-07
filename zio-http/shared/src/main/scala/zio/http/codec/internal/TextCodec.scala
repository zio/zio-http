package zio.http.codec.internal

import java.time._
import java.util.UUID

import zio._

import zio.stream._

import zio.schema._
import zio.schema.codec._

private[codec] object TextCodec {
  def fromSchema[A](schema: Schema[A]): BinaryCodec[A] = {
    if (!schema.isInstanceOf[Schema.Primitive[_]]) {
      new BinaryCodec[A] {
        override def decode(whole: Chunk[Byte]): Either[DecodeError, A] = throw new IllegalArgumentException(
          s"Schema $schema is not a primitive. Only primitive schemas are supported by TextCodec.",
        )

        override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] = throw new IllegalArgumentException(
          s"Schema $schema is not a primitive. Only primitive schemas are supported by TextCodec.",
        )

        override def encode(value: A): Chunk[Byte] = throw new IllegalArgumentException(
          s"Schema $schema is not a primitive. Only primitive schemas are supported by TextCodec.",
        )

        override def streamEncoder: ZPipeline[Any, Nothing, A, Byte] = throw new IllegalArgumentException(
          s"Schema $schema is not a primitive. Only primitive schemas are supported by TextCodec.",
        )
      }
    } else {
      new BinaryCodec[A] {
        override def encode(a: A): Chunk[Byte] =
          schema match {
            case Schema.Primitive(_, _) => Chunk.fromArray(a.toString.getBytes)
            case _                      =>
              throw new IllegalArgumentException(
                s"Cannot encode $a of type ${a.getClass} with schema $schema",
              )
          }

        override def decode(c: Chunk[Byte]): Either[DecodeError, A] = {
          val s = c.asString
          schema match {
            case Schema.Primitive(standardType, _) =>
              (standardType match {
                case StandardType.UnitType           =>
                  Right("")
                case StandardType.StringType         =>
                  Right(s)
                case StandardType.BoolType           =>
                  try {
                    Right(s.toBoolean)
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.ByteType           =>
                  try {
                    Right(s.toByte)
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.ShortType          =>
                  try {
                    Right(s.toShort)
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.IntType            =>
                  try {
                    Right(s.toInt)
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.LongType           =>
                  try {
                    Right(s.toLong)
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.FloatType          =>
                  try {
                    Right(s.toFloat)
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.DoubleType         =>
                  try {
                    Right(s.toDouble)
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.BinaryType         =>
                  Left(DecodeError.UnsupportedSchema(schema, "TextCodec"))
                case StandardType.CharType           =>
                  Right(s.charAt(0))
                case StandardType.UUIDType           =>
                  try {
                    Right(UUID.fromString(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.BigDecimalType     =>
                  try {
                    Right(BigDecimal(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.BigIntegerType     =>
                  try {
                    Right(BigInt(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.DayOfWeekType      =>
                  try {
                    Right(DayOfWeek.valueOf(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.MonthType          =>
                  try {
                    Right(Month.valueOf(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.MonthDayType       =>
                  try {
                    Right(MonthDay.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.PeriodType         =>
                  try {
                    Right(Period.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.YearType           =>
                  try {
                    Right(Year.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.YearMonthType      =>
                  try {
                    Right(YearMonth.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.ZoneIdType         =>
                  try {
                    Right(ZoneId.of(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.ZoneOffsetType     =>
                  try {
                    Right(ZoneOffset.of(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.DurationType       =>
                  try {
                    Right(java.time.Duration.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.InstantType        =>
                  try {
                    Right(Instant.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.LocalDateType      =>
                  try {
                    Right(LocalDate.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.LocalTimeType      =>
                  try {
                    Right(LocalTime.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.LocalDateTimeType  =>
                  try {
                    Right(LocalDateTime.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.OffsetTimeType     =>
                  try {
                    Right(OffsetTime.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.OffsetDateTimeType =>
                  try {
                    Right(OffsetDateTime.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
                case StandardType.ZonedDateTimeType  =>
                  try {
                    Right(ZonedDateTime.parse(s))
                  } catch {
                    case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                  }
              }).map(_.asInstanceOf[A])
            case schema                            =>
              Left(
                DecodeError.UnsupportedSchema(schema, "Only primitive types are supported for text decoding."),
              )
          }
        }

        override def streamEncoder: ZPipeline[Any, Nothing, A, Byte] =
          ZPipeline.map((a: A) => Chunk.fromArray(a.toString.getBytes)).flattenChunks

        override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
          (ZPipeline[Byte] >>> ZPipeline.utf8Decode)
            .map(s => decode(Chunk.fromArray(s.getBytes)).fold(throw _, identity))
            .mapErrorCause(e => Cause.fail(DecodeError.ReadError(e, e.squash.getMessage)))
      }
    }
  }
}
