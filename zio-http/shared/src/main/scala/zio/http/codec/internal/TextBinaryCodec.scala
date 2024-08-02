package zio.http.codec.internal

import java.time._
import java.util.{Currency, UUID}

import zio._

import zio.stream._

import zio.schema._
import zio.schema.codec._

import zio.http.codec.CodecBuilder

object TextBinaryCodec {
  private def errorCodec[A](schema: Schema[A]) =
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

  val codecBuilder = new CodecBuilder {
    override def build[T](schema: Schema[T]): BinaryCodec[T] = TextBinaryCodec.fromSchema[T](schema)
  }

  implicit def fromSchema[A](implicit schema: Schema[A]): BinaryCodec[A] = {
    schema match {
      case Schema.Optional(schema, annotations)                =>
        val codec = fromSchema(schema).asInstanceOf[BinaryCodec[Any]]
        new BinaryCodec[A] {
          override def encode(a: A): Chunk[Byte] = {
            a match {
              case Some(value) => codec.encode(value)
              case None        => Chunk.empty
            }
          }

          override def decode(c: Chunk[Byte]): Either[DecodeError, A]      =
            if (c.isEmpty) Right(None.asInstanceOf[A])
            else codec.decode(c).map(Some(_)).asInstanceOf[Either[DecodeError, A]]
          override def streamEncoder: ZPipeline[Any, Nothing, A, Byte]     =
            ZPipeline.map(a => encode(a)).flattenChunks
          override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
            codec.streamDecoder.map(v => Some(v).asInstanceOf[A])
        }
      case enum0: Schema.Enum[_]                               => errorCodec(enum0)
      case record: Schema.Record[_] if record.fields.size == 1 =>
        val fieldSchema = record.fields.head.schema
        val codec       = fromSchema(fieldSchema).asInstanceOf[BinaryCodec[A]]
        new BinaryCodec[A] {
          override def encode(a: A): Chunk[Byte]                           =
            codec.encode(record.deconstruct(a)(Unsafe.unsafe).head.get.asInstanceOf[A])
          override def decode(c: Chunk[Byte]): Either[DecodeError, A]      =
            codec
              .decode(c)
              .flatMap(a =>
                record.construct(Chunk(a))(Unsafe.unsafe).left.map(s => DecodeError.ReadError(Cause.empty, s)),
              )
          override def streamEncoder: ZPipeline[Any, Nothing, A, Byte]     =
            ZPipeline.map(a => encode(a)).flattenChunks
          override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
            codec.streamDecoder.mapZIO(a =>
              ZIO.fromEither(
                record.construct(Chunk(a))(Unsafe.unsafe).left.map(s => DecodeError.ReadError(Cause.empty, s)),
              ),
            )
        }
      case record: Schema.Record[_]                            => errorCodec(record)
      case collection: Schema.Collection[_, _]                 => errorCodec(collection)
      case Schema.Transform(schema, f, g, _, _)                =>
        val codec = fromSchema(schema)
        new BinaryCodec[A] {
          override def encode(a: A): Chunk[Byte] = codec.encode(g(a).fold(e => throw new Exception(e), identity))
          override def decode(c: Chunk[Byte]): Either[DecodeError, A]      = codec
            .decode(c)
            .flatMap(x =>
              f(x).left
                .map(DecodeError.ReadError(Cause.fail(new Exception("Error during decoding")), _)),
            )
          override def streamEncoder: ZPipeline[Any, Nothing, A, Byte]     =
            ZPipeline.mapChunks(_.flatMap(encode))
          override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] = codec.streamDecoder.map { x =>
            f(x) match {
              case Left(value) => throw DecodeError.ReadError(Cause.fail(new Exception("Error in decoding")), value)
              case Right(a)    => a
            }
          }
        }
      case Schema.Primitive(_, _)                              =>
        new BinaryCodec[A] {
          val decode0: String => Either[DecodeError, Any] =
            schema match {
              case Schema.Primitive(standardType, _) =>
                standardType match {
                  case StandardType.UnitType           =>
                    val result = Right("")
                    (_: String) => result
                  case StandardType.StringType         =>
                    (s: String) => Right(s)
                  case StandardType.BoolType           =>
                    (s: String) =>
                      s.toLowerCase match {
                        case "true" | "on" | "yes" | "1"  => Right(true)
                        case "false" | "off" | "no" | "0" => Right(false)
                        case _ => Left(DecodeError.ReadError(Cause.fail(new Exception("Invalid boolean value")), s))
                      }
                  case StandardType.ByteType           =>
                    (s: String) =>
                      try {
                        Right(s.toByte)
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.ShortType          =>
                    (s: String) =>
                      try {
                        Right(s.toShort)
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.IntType            =>
                    (s: String) =>
                      try {
                        Right(s.toInt)
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.LongType           =>
                    (s: String) =>
                      try {
                        Right(s.toLong)
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.FloatType          =>
                    (s: String) =>
                      try {
                        Right(s.toFloat)
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.DoubleType         =>
                    (s: String) =>
                      try {
                        Right(s.toDouble)
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.BinaryType         =>
                    val result = Left(DecodeError.UnsupportedSchema(schema, "TextCodec"))
                    (_: String) => result
                  case StandardType.CharType           =>
                    (s: String) => Right(s.charAt(0))
                  case StandardType.UUIDType           =>
                    (s: String) =>
                      try {
                        Right(UUID.fromString(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.BigDecimalType     =>
                    (s: String) =>
                      try {
                        Right(BigDecimal(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.BigIntegerType     =>
                    (s: String) =>
                      try {
                        Right(BigInt(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.DayOfWeekType      =>
                    (s: String) =>
                      try {
                        Right(DayOfWeek.valueOf(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.MonthType          =>
                    (s: String) =>
                      try {
                        Right(Month.valueOf(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.MonthDayType       =>
                    (s: String) =>
                      try {
                        Right(MonthDay.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.PeriodType         =>
                    (s: String) =>
                      try {
                        Right(Period.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.YearType           =>
                    (s: String) =>
                      try {
                        Right(Year.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.YearMonthType      =>
                    (s: String) =>
                      try {
                        Right(YearMonth.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.ZoneIdType         =>
                    (s: String) =>
                      try {
                        Right(ZoneId.of(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.ZoneOffsetType     =>
                    (s: String) =>
                      try {
                        Right(ZoneOffset.of(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.DurationType       =>
                    (s: String) =>
                      try {
                        Right(java.time.Duration.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.InstantType        =>
                    (s: String) =>
                      try {
                        Right(Instant.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.LocalDateType      =>
                    (s: String) =>
                      try {
                        Right(LocalDate.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.LocalTimeType      =>
                    (s: String) =>
                      try {
                        Right(LocalTime.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.LocalDateTimeType  =>
                    (s: String) =>
                      try {
                        Right(LocalDateTime.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.OffsetTimeType     =>
                    (s: String) =>
                      try {
                        Right(OffsetTime.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.OffsetDateTimeType =>
                    (s: String) =>
                      try {
                        Right(OffsetDateTime.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.ZonedDateTimeType  =>
                    (s: String) =>
                      try {
                        Right(ZonedDateTime.parse(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                  case StandardType.CurrencyType       =>
                    (s: String) =>
                      try {
                        Right(Currency.getInstance(s))
                      } catch {
                        case e: Exception => Left(DecodeError.ReadError(Cause.fail(e), e.getMessage))
                      }
                }
              case schema                            =>
                val result = Left(
                  DecodeError.UnsupportedSchema(schema, "Only primitive types are supported for text decoding."),
                )
                (_: String) => result
            }
          override def encode(a: A): Chunk[Byte]          =
            schema match {
              case Schema.Primitive(_, _) => Chunk.fromArray(a.toString.getBytes)
              case _                      =>
                throw new IllegalArgumentException(
                  s"Cannot encode $a of type ${a.getClass} with schema $schema",
                )
            }

          override def decode(c: Chunk[Byte]): Either[DecodeError, A] =
            decode0(c.asString).map(_.asInstanceOf[A])

          override def streamEncoder: ZPipeline[Any, Nothing, A, Byte] =
            ZPipeline.map((a: A) => Chunk.fromArray(a.toString.getBytes)).flattenChunks

          override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
            (ZPipeline[Byte] >>> ZPipeline.utf8Decode)
              .map(s => decode(Chunk.fromArray(s.getBytes)).fold(throw _, identity))
              .mapErrorCause(e => Cause.fail(DecodeError.ReadError(e, e.squash.getMessage)))
        }
      case Schema.Lazy(schema0)                                => fromSchema(schema0())
      case _                                                   => errorCodec(schema)
    }
  }
}
