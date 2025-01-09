package zio.http.codec

import java.time._
import java.util.{Currency, UUID}

import scala.annotation.tailrec

import zio._

import zio.stream._

import zio.schema._
import zio.schema.annotation.simpleEnum
import zio.schema.codec._

import zio.http.Charsets

object StringCodec {
  type StringCodec[A] = Codec[String, Char, A]
  private def errorCodec[A](schema: Schema[A]) =
    new Codec[String, Char, A] {
      override def decode(whole: String): Either[DecodeError, A] = throw new IllegalArgumentException(
        s"Schema $schema is not supported by StringCodec.",
      )

      override def streamDecoder: ZPipeline[Any, DecodeError, Char, A] = throw new IllegalArgumentException(
        s"Schema $schema is not supported by StringCodec.",
      )

      override def encode(value: A): String = throw new IllegalArgumentException(
        s"Schema $schema is not supported by StringCodec.",
      )

      override def streamEncoder: ZPipeline[Any, Nothing, A, Char] = throw new IllegalArgumentException(
        s"Schema $schema is not supported by StringCodec.",
      )
    }

  @tailrec
  private def emptyStringIsValue(schema: Schema[_]): Boolean = {
    schema match {
      case value: Schema.Optional[_] =>
        val innerSchema = value.schema
        emptyStringIsValue(innerSchema)
      case _                         =>
        schema.asInstanceOf[Schema.Primitive[_]].standardType match {
          case StandardType.UnitType   => true
          case StandardType.StringType => true
          case StandardType.BinaryType => true
          case StandardType.CharType   => true
          case _                       => false
        }
    }
  }

  implicit def fromSchema[A](implicit schema: Schema[A]): Codec[String, Char, A] = {
    schema match {
      case Schema.Optional(schema, _)                                                    =>
        val codec = fromSchema(schema).asInstanceOf[Codec[String, Char, Any]]
        new Codec[String, Char, A] {
          override def encode(a: A): String = {
            a match {
              case Some(value) => codec.encode(value)
              case None        => ""
            }
          }

          override def decode(c: String): Either[DecodeError, A] = {
            if (c.isEmpty && !emptyStringIsValue(schema)) Right(None.asInstanceOf[A])
            else {
              codec.decode(c).map(Some(_)).asInstanceOf[Either[DecodeError, A]]
            }
          }

          override def streamEncoder: ZPipeline[Any, Nothing, A, Char]     =
            ZPipeline.map((a: A) => encode(a).toSeq).flattenIterables
          override def streamDecoder: ZPipeline[Any, DecodeError, Char, A] =
            codec.streamDecoder.map(v => Some(v).asInstanceOf[A])
        }
      case enum0: Schema.Enum[_] if enum0.annotations.exists(_.isInstanceOf[simpleEnum]) =>
        val stringCodec    = fromSchema(Schema.Primitive(StandardType.StringType))
        val caseMap        = enum0.nonTransientCases
          .map(case_ =>
            case_.schema.asInstanceOf[Schema.CaseClass0[A]].defaultConstruct() ->
              case_.caseName,
          )
          .toMap
        val reverseCaseMap = caseMap.map(_.swap)
        new Codec[String, Char, A] {
          override def encode(a: A): String = {
            val caseName = caseMap(a.asInstanceOf[A])
            stringCodec.encode(caseName)
          }

          override def decode(c: String): Either[DecodeError, A]           =
            stringCodec.decode(c).flatMap { caseName =>
              reverseCaseMap.get(caseName) match {
                case Some(value) => Right(value.asInstanceOf[A])
                case None        => Left(DecodeError.MissingCase(caseName, enum0))
              }
            }
          override def streamEncoder: ZPipeline[Any, Nothing, A, Char]     =
            ZPipeline.map((a: A) => encode(a).toSeq).flattenIterables
          override def streamDecoder: ZPipeline[Any, DecodeError, Char, A] =
            stringCodec.streamDecoder.mapZIO { caseName =>
              reverseCaseMap.get(caseName) match {
                case Some(value) => ZIO.succeed(value.asInstanceOf[A])
                case None        => ZIO.fail(DecodeError.MissingCase(caseName, enum0))
              }
            }
        }

      case enum0: Schema.Enum[_]                               => errorCodec(enum0)
      case record: Schema.Record[_] if record.fields.size == 1 =>
        val fieldSchema = record.fields.head.schema
        val codec       = fromSchema(fieldSchema).asInstanceOf[Codec[String, Char, A]]
        new Codec[String, Char, A] {
          override def encode(a: A): String                                =
            codec.encode(record.deconstruct(a)(Unsafe.unsafe).head.get.asInstanceOf[A])
          override def decode(c: String): Either[DecodeError, A]           =
            codec
              .decode(c)
              .flatMap(a =>
                record.construct(Chunk(a))(Unsafe.unsafe).left.map(s => DecodeError.ReadError(Cause.empty, s)),
              )
          override def streamEncoder: ZPipeline[Any, Nothing, A, Char]     =
            ZPipeline.map((a: A) => encode(a).toSeq).flattenIterables
          override def streamDecoder: ZPipeline[Any, DecodeError, Char, A] =
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
        new Codec[String, Char, A] {
          override def encode(a: A): String = codec.encode(g(a).fold(e => throw new Exception(e), identity))
          override def decode(c: String): Either[DecodeError, A]           = codec
            .decode(c)
            .flatMap(x =>
              f(x).left
                .map(DecodeError.ReadError(Cause.fail(new Exception("Error during decoding")), _)),
            )
          override def streamEncoder: ZPipeline[Any, Nothing, A, Char]     =
            ZPipeline.mapChunks(_.flatMap(encode))
          override def streamDecoder: ZPipeline[Any, DecodeError, Char, A] = codec.streamDecoder.map { x =>
            f(x) match {
              case Left(value) => throw DecodeError.ReadError(Cause.fail(new Exception("Error in decoding")), value)
              case Right(a)    => a
            }
          }
        }
      case Schema.Primitive(_, _)                              =>
        new Codec[String, Char, A] {
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
          override def encode(a: A): String               =
            schema match {
              case Schema.Primitive(_, _) => a.toString
              case _                      =>
                throw new IllegalArgumentException(
                  s"Cannot encode $a of type ${a.getClass} with schema $schema",
                )
            }

          override def decode(c: String): Either[DecodeError, A] =
            decode0(c).map(_.asInstanceOf[A])

          override def streamEncoder: ZPipeline[Any, Nothing, A, Char] =
            ZPipeline.map((a: A) => a.toString.toSeq).flattenIterables

          override def streamDecoder: ZPipeline[Any, DecodeError, Char, A] =
            ZPipeline
              .chunks[Char]
              .map(_.asString)
              .mapZIO(s => ZIO.fromEither(decode(s)))
              .mapErrorCause(e => Cause.fail(DecodeError.ReadError(e, e.squash.getMessage)))
        }
      case Schema.Lazy(schema0)                                => fromSchema(schema0())
      case _                                                   => errorCodec(schema)
    }
  }
}
