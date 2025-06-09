package zio.http.internal

import java.time._
import java.util.{Currency, UUID}

import scala.annotation.tailrec
import scala.util.Try

import zio.prelude.{NonEmptyList, NonEmptySet}
import zio.{Cause, Chunk, NonEmptyChunk, Unsafe}

import zio.schema.codec.DecodeError
import zio.schema.validation.{Validation, ValidationError}
import zio.schema.{Schema, StandardType, TypeId}

import zio.http.codec.HttpCodecError
import zio.http.internal.StringSchemaCodec.{PrimitiveCodec, decodeAndUnwrap, emptyStringIsValue, validateDecoded}
import zio.http.{Headers, QueryParams}

private[http] trait ErrorConstructor {
  def missing(fieldName: String): HttpCodecError
  def missingAll(fieldNames: Chunk[String]): HttpCodecError
  def invalid(errors: Chunk[ValidationError]): HttpCodecError
  def malformed(fieldName: String, error: DecodeError): HttpCodecError
  def invalidCount(fieldName: String, expected: Int, actual: Int): HttpCodecError
}

private[http] object ErrorConstructor {
  private[http] val query = new ErrorConstructor {
    override def missing(fieldName: String): HttpCodecError =
      HttpCodecError.MissingQueryParam(fieldName)

    override def missingAll(fieldNames: Chunk[String]): HttpCodecError =
      HttpCodecError.MissingQueryParams(fieldNames)

    override def invalid(errors: Chunk[ValidationError]): HttpCodecError =
      HttpCodecError.InvalidEntity.wrap(errors)

    override def malformed(fieldName: String, error: DecodeError): HttpCodecError =
      HttpCodecError.MalformedQueryParam(fieldName, error)

    override def invalidCount(fieldName: String, expected: Int, actual: Int): HttpCodecError =
      HttpCodecError.InvalidQueryParamCount(fieldName, expected, actual)
  }

  private[http] val header = new ErrorConstructor {
    override def missing(fieldName: String): HttpCodecError =
      HttpCodecError.MissingHeader(fieldName)

    override def missingAll(fieldNames: Chunk[String]): HttpCodecError =
      HttpCodecError.MissingHeaders(fieldNames)

    override def invalid(errors: Chunk[ValidationError]): HttpCodecError =
      HttpCodecError.InvalidEntity.wrap(errors)

    override def malformed(fieldName: String, error: DecodeError): HttpCodecError =
      HttpCodecError.DecodingErrorHeader(fieldName, error)

    override def invalidCount(fieldName: String, expected: Int, actual: Int): HttpCodecError =
      HttpCodecError.InvalidHeaderCount(fieldName, expected, actual)
  }

}

private[http] trait StringSchemaCodec[A, Target] {
  private[http] val defaultValue: A
  private[http] val isOptional: Boolean
  private[http] val isOptionalSchema: Boolean
  private[http] val recordFields: Chunk[(Schema.Field[_, _], PrimitiveCodec[Any])] = {
    val fields = schema match {
      case record: Schema.Record[A]                                                =>
        record.fields
      case s: Schema.Optional[_] if s.schema.isInstanceOf[Schema.Record[_]]        =>
        s.schema.asInstanceOf[Schema.Record[A]].fields
      case s: Schema.Transform[_, _, _] if s.schema.isInstanceOf[Schema.Record[_]] =>
        s.schema.asInstanceOf[Schema.Record[A]].fields
      case _                                                                       => Chunk.empty
    }
    fields.map(StringSchemaCodec.unlazyField).map {
      case field if field.schema.isInstanceOf[Schema.Collection[_, _]] =>
        val elementSchema = field.schema.asInstanceOf[Schema.Collection[_, _]] match {
          case s: Schema.NonEmptySequence[_, _, _] => s.elementSchema
          case s: Schema.Sequence[_, _, _]         => s.elementSchema
          case s: Schema.Set[_]                    => s.elementSchema
          case _: Schema.Map[_, _]                 => throw new IllegalArgumentException("Maps are not supported")
          case _: Schema.NonEmptyMap[_, _]         => throw new IllegalArgumentException("Maps are not supported")
        }
        val codec         = PrimitiveCodec(elementSchema).asInstanceOf[PrimitiveCodec[Any]]
        (StringSchemaCodec.mapFieldName(field, kebabCase), codec)
      case field                                                       =>
        val codec =
          PrimitiveCodec(field.annotations.foldLeft(field.schema)(_.annotate(_))).asInstanceOf[PrimitiveCodec[Any]]
        (StringSchemaCodec.mapFieldName(field, kebabCase), codec)
    }
  }
  private[http] val recordSchema: Schema.Record[Any]                               = schema match {
    case record: Schema.Record[_]                                         =>
      record.asInstanceOf[Schema.Record[Any]]
    case s: Schema.Optional[_] if s.schema.isInstanceOf[Schema.Record[_]] =>
      s.schema.asInstanceOf[Schema.Record[Any]]
    case _                                                                => null
  }

  private[http] def schema: Schema[A]

  private[http] def add(target: Target, key: String, value: String): Target

  private[http] def addAll(target: Target, headers: Iterable[(String, String)]): Target

  private[http] def contains(target: Target, key: String): Boolean

  private[http] def unsafeGet(target: Target, key: String): String

  private[http] def getAll(target: Target, key: String): Chunk[String]

  private[http] def count(target: Target, key: String): Int

  private[http] def error: ErrorConstructor

  private[http] def kebabCase: Boolean

  private def createAndValidateCollection(schema: Schema.Collection[_, _], decoded: Chunk[Any]) = {
    val collection       = schema.fromChunk.asInstanceOf[Chunk[Any] => Any](decoded)
    val erasedSchema     = schema.asInstanceOf[Schema[Any]]
    val validationErrors = erasedSchema.validate(collection)(erasedSchema)
    if (validationErrors.nonEmpty) throw error.invalid(validationErrors)
    collection
  }

  private[http] def decode(target: Target): A = {
    val optional     = isOptionalSchema
    val hasDefault   = defaultValue != null && isOptional
    val default      = defaultValue
    val hasAllParams = recordFields.forall { case (field, codec) =>
      contains(target, field.fieldName) || field.optional || codec.isOptional || (field.schema
        .isInstanceOf[Schema.Collection[_, _]] && field.defaultValue.isDefined)
    }
    if (!hasAllParams && hasDefault) default
    else if (!hasAllParams) {
      throw error.missingAll {
        recordFields.collect {
          case (field, codec) if !(contains(target, field.fieldName) || field.optional || codec.isOptional) =>
            field.fieldName
        }
      }
    } else {
      val decoded = recordFields.map {
        case (field, codec) if field.schema.isInstanceOf[Schema.Collection[_, _]] =>
          val schema = field.schema.asInstanceOf[Schema.Collection[_, _]]
          if (!contains(target, field.fieldName)) {
            if (field.defaultValue.isDefined) field.defaultValue.get
            else throw error.missing(field.fieldName)
          } else {
            val values  = getAll(target, field.fieldName)
            val decoded =
              values.map(decodeAndUnwrap(field, codec, _, error.malformed))
            createAndValidateCollection(schema, decoded)

          }
        case (field, codec)                                                       =>
          val count0  = count(target, field.fieldName)
          if (count0 > 1) throw error.invalidCount(field.fieldName, 1, count0)
          val value   = unsafeGet(target, field.fieldName)
          val decoded = {
            if (value == null || (value == "" && !emptyStringIsValue(codec.schema) && codec.isOptional))
              codec.defaultValue
            else decodeAndUnwrap(field, codec, value, error.malformed)
          }
          validateDecoded(codec, decoded, error)
      }
      if (optional) {
        val constructed = recordSchema.construct(decoded)(Unsafe.unsafe)
        constructed match {
          case Left(value)  =>
            throw error.malformed(
              s"${recordSchema.id}",
              DecodeError.ReadError(Cause.empty, value),
            )
          case Right(value) =>
            recordSchema.validate(value)(recordSchema) match {
              case errors if errors.nonEmpty => throw error.invalid(errors)
              case _ if value.isInstanceOf[Iterable[_]] && value.asInstanceOf[Iterable[_]].isEmpty =>
                None.asInstanceOf[A]
              case _ => Some(value).asInstanceOf[A]
            }
        }
      } else {
        val constructed = recordSchema.construct(decoded)(Unsafe.unsafe)
        constructed match {
          case Left(value)  =>
            throw error.malformed(
              s"${recordSchema.id}",
              DecodeError.ReadError(Cause.empty, value),
            )
          case Right(value) =>
            recordSchema.validate(value)(recordSchema) match {
              case errors if errors.nonEmpty => throw error.invalid(errors)
              case _                         => value.asInstanceOf[A]
            }
        }
      }
    }

  }

  private[http] def encode(input: A, target: Target): Target = {
    val fields = recordFields
    val value  = input.asInstanceOf[Any] match {
      case None                          => null
      case it: Iterable[_] if it.isEmpty => null
      case Some(value)                   => value
      case value                         => value
    }
    if (value == null) target
    else {
      val fieldValues   = recordSchema.deconstruct(value)(Unsafe.unsafe)
      var target0       = target
      val fieldIt       = fields.iterator
      val fieldValuesIt = fieldValues.iterator
      while (fieldIt.hasNext) {
        val (field, codec) = fieldIt.next()
        val name           = field.fieldName
        val value          = fieldValuesIt.next() match {
          case Some(value) => nonEmptyAsIterable(value)
          case None        => field.defaultValue
        }
        value match {
          case values: Iterable[_] =>
            target0 = addAll(target0, values.map { v => (name, codec.encode(v)) })
          case _                   =>
            val encoded = codec.encode(value)
            if (encoded != null) target0 = add(target0, name, encoded)
        }
      }
      target0
    }
  }

  private def nonEmptyAsIterable(value: Any): Any = value match {
    case c: NonEmptyChunk[_] => c.toChunk
    case s: NonEmptySet[_]   => s.toSet
    case l: NonEmptyList[_]  => l.toList
    case it                  => it
  }
  private[http] def optional: StringSchemaCodec[Option[A], Target]

}

private[http] object StringSchemaCodec {
  private[http] def unlazyField(field: Schema.Field[_, _]): Schema.Field[_, _] = field match {
    case f if f.schema.isInstanceOf[Schema.Lazy[_]] =>
      Schema.Field(
        f.name,
        f.schema.asInstanceOf[Schema.Lazy[_]].schema.asInstanceOf[Schema[Any]],
        f.annotations,
        f.validation.asInstanceOf[Validation[Any]],
        f.get.asInstanceOf[Any => Any],
        f.set.asInstanceOf[(Any, Any) => Any],
      )
    case f                                          => f
  }
  private[http] def defaultValue[A](schema: Schema[A]): A                      =
    if (schema.isInstanceOf[Schema.Collection[_, _]]) {
      Try(schema.asInstanceOf[Schema.Collection[A, _]].empty).fold(
        _ => null.asInstanceOf[A],
        identity,
      )
    } else {
      schema.defaultValue match {
        case Right(value) => value
        case Left(_)      =>
          schema match {
            case _: Schema.Optional[_]               => None.asInstanceOf[A]
            case collection: Schema.Collection[A, _] =>
              Try(collection.empty).fold(
                _ => null.asInstanceOf[A],
                identity,
              )
            case _                                   => null.asInstanceOf[A]
          }
      }
    }

  private[http] def isOptional(schema: Schema[_]): Boolean = schema match {
    case _: Schema.Optional[_]      =>
      true
    case record: Schema.Record[_]   =>
      record.fields.forall(_.optional) || record.defaultValue.isRight
    case d: Schema.Collection[_, _] =>
      val bool = Try(d.empty).isSuccess || d.defaultValue.isRight
      bool
    case _                          =>
      false
  }

  private[http] def isOptionalSchema(schema: Schema[_]): Boolean =
    schema match {
      case _: Schema.Optional[_]                                                     => true
      case s: Schema.Transform[_, _, _] if s.schema.isInstanceOf[Schema.Optional[_]] => true
      case _                                                                         => false
    }

  private def decodeAndUnwrap(
    field: Schema.Field[_, _],
    codec: PrimitiveCodec[Any],
    value: String,
    ex: (String, DecodeError) => HttpCodecError,
  ) =
    try codec.decode(value)
    catch {
      case err: DecodeError => throw ex(field.fieldName, err)
    }

  private def validateDecoded(codec: PrimitiveCodec[Any], decoded: Any, error: ErrorConstructor) = {
    val validationErrors = codec.schema.validate(decoded)(codec.schema)
    if (validationErrors.nonEmpty) throw error.invalid(validationErrors)
    decoded
  }

  @tailrec
  private def emptyStringIsValue(schema: Schema[_]): Boolean = {
    schema match {
      case value: Schema.Optional[_]        =>
        val innerSchema = value.schema
        emptyStringIsValue(innerSchema)
      case value: Schema.Transform[_, _, _] =>
        val innerSchema = value.schema
        emptyStringIsValue(innerSchema)
      case _                                =>
        schema.asInstanceOf[Schema.Primitive[_]].standardType match {
          case StandardType.UnitType   => true
          case StandardType.StringType => true
          case StandardType.BinaryType => true
          case StandardType.CharType   => true
          case _                       => false
        }
    }
  }

  private[http] def mapFieldName(field: Schema.Field[_, _], kebabCase: Boolean): Schema.Field[_, _] = {
    Schema.Field(
      if (!kebabCase) field.fieldName else camelToKebab(field.fieldName),
      field.annotations.foldLeft(field.schema)(_ annotate _).asInstanceOf[Schema[Any]],
      field.annotations,
      field.validation.asInstanceOf[Validation[Any]],
      field.get.asInstanceOf[Any => Any],
      field.set.asInstanceOf[(Any, Any) => Any],
    )
  }

  private[http] def headerFromSchema[A](
    schema0: Schema[A],
    error0: ErrorConstructor,
    name: String,
  ): StringSchemaCodec[A, Headers] = {

    def stringSchemaCodec(schema1: Schema[Any]): StringSchemaCodec[A, Headers] =
      new StringSchemaCodec[A, Headers] {
        override def schema: Schema[A] = schema1.asInstanceOf[Schema[A]]

        override private[http] def add(headers: Headers, key: String, value: String): Headers =
          headers.addHeaders(Headers.apply(key, value))

        override private[http] def addAll(headers: Headers, kvs: Iterable[(String, String)]): Headers =
          headers.addHeaders(kvs)

        override private[http] def contains(headers: Headers, key: String): Boolean =
          headers.contains(key)

        override private[http] def unsafeGet(headers: Headers, key: String): String =
          headers.getUnsafe(key)

        override private[http] def getAll(headers: Headers, key: String): Chunk[String] =
          headers.rawHeaders(key)

        override private[http] def count(headers: Headers, key: String): Int =
          headers.rawHeaders(key).size

        override private[http] def error: ErrorConstructor =
          error0

        override private[http] def optional: StringSchemaCodec[Option[A], Headers] =
          StringSchemaCodec.headerFromSchema(schema.optional, error0, name)

        override private[http] def kebabCase: Boolean        =
          true
        override private[http] val defaultValue: A           =
          StringSchemaCodec.defaultValue(schema0)
        override private[http] val isOptional: Boolean       =
          StringSchemaCodec.isOptional(schema0)
        override private[http] val isOptionalSchema: Boolean =
          StringSchemaCodec.isOptionalSchema(schema0)
      }
    schema0 match {
      case s @ Schema.Primitive(_, _)                                  =>
        stringSchemaCodec(recordSchema(s.asInstanceOf[Schema[Any]], name))
      case s @ Schema.Optional(schema, _)                              =>
        schema match {
          case _: Schema.Collection[_, _] | _: Schema.Primitive[_] =>
            stringSchemaCodec(recordSchema(schema.asInstanceOf[Schema[Any]], name))
          case s if s.isInstanceOf[Schema.Record[_]] => stringSchemaCodec(schema.asInstanceOf[Schema[Any]])
          case _                                     => throw new IllegalArgumentException(s"Unsupported schema $s")
        }
      case s @ Schema.Transform(schema, _, _, _, _)                    =>
        schema match {
          case _: Schema.Collection[_, _] | _: Schema.Primitive[_] =>
            stringSchemaCodec(recordSchema(s.asInstanceOf[Schema[Any]], name))
          case _: Schema.Record[_]                                 => stringSchemaCodec(s.asInstanceOf[Schema[Any]])
          case _ => throw new IllegalArgumentException(s"Unsupported schema $s")
        }
      case Schema.Lazy(schema0)                                        =>
        headerFromSchema(
          schema0().asInstanceOf[Schema[A]],
          error0,
          name,
        )
      case _: Schema.Collection[_, _]                                  =>
        stringSchemaCodec(recordSchema(schema0.asInstanceOf[Schema[Any]], name))
      case s: Schema.Record[_] if s.fields.size == 1 && (name ne null) =>
        stringSchemaCodec(recordSchema(s.asInstanceOf[Schema[Any]], name))
      case s: Schema.Record[_]                                         =>
        stringSchemaCodec(s.asInstanceOf[Schema[Any]])
      case _                                                           =>
        throw new IllegalArgumentException(s"Unsupported schema $schema0")

    }

  }

  private[http] def queryFromSchema[A](
    schema0: Schema[A],
    error0: ErrorConstructor,
    name: String,
  ): StringSchemaCodec[A, QueryParams] = {

    def stringSchemaCodec(schema1: Schema[Any]): StringSchemaCodec[A, QueryParams] =
      new StringSchemaCodec[A, QueryParams] {
        override def schema: Schema[A] = schema1.asInstanceOf[Schema[A]]

        override private[http] def add(queryParams: QueryParams, key: String, value: String): QueryParams =
          queryParams.addQueryParam(key, value)

        override private[http] def addAll(queryParams: QueryParams, kvs: Iterable[(String, String)]): QueryParams =
          queryParams.addQueryParams(kvs)

        override private[http] def contains(queryParams: QueryParams, key: String): Boolean =
          queryParams.hasQueryParam(key)

        override private[http] def unsafeGet(queryParams: QueryParams, key: String): String =
          queryParams.unsafeQueryParam(key)

        override private[http] def getAll(queryParams: QueryParams, key: String): Chunk[String] =
          queryParams.getAll(key)

        override private[http] def count(queryParams: QueryParams, key: String): Int =
          queryParams.valueCount(key)

        override private[http] def error: ErrorConstructor =
          error0

        override private[http] def optional: StringSchemaCodec[Option[A], QueryParams] =
          StringSchemaCodec.queryFromSchema(schema.optional, error0, name)

        override private[http] def kebabCase: Boolean        =
          false
        override private[http] val defaultValue: A           =
          StringSchemaCodec.defaultValue(schema0)
        override private[http] val isOptional: Boolean       =
          StringSchemaCodec.isOptional(schema0)
        override private[http] val isOptionalSchema: Boolean =
          StringSchemaCodec.isOptionalSchema(schema0)
      }
    schema0 match {
      case s @ Schema.Primitive(_, _)                                  =>
        stringSchemaCodec(recordSchema(s.asInstanceOf[Schema[Any]], name))
      case s @ Schema.Optional(schema, _)                              =>
        schema match {
          case _: Schema.Collection[_, _] | _: Schema.Primitive[_] =>
            stringSchemaCodec(recordSchema(schema.asInstanceOf[Schema[Any]], name))
          case s if s.isInstanceOf[Schema.Record[_]] => stringSchemaCodec(schema.asInstanceOf[Schema[Any]])
          case _                                     => throw new IllegalArgumentException(s"Unsupported schema $s")
        }
      case s @ Schema.Transform(schema, _, _, _, _)                    =>
        schema match {
          case _: Schema.Collection[_, _] | _: Schema.Primitive[_] =>
            stringSchemaCodec(recordSchema(s.asInstanceOf[Schema[Any]], name))
          case _: Schema.Record[_]                                 => stringSchemaCodec(s.asInstanceOf[Schema[Any]])
          case _ => throw new IllegalArgumentException(s"Unsupported schema $s")
        }
      case Schema.Lazy(schema0)                                        =>
        queryFromSchema(
          schema0().asInstanceOf[Schema[A]],
          error0,
          name,
        )
      case _: Schema.Collection[_, _]                                  =>
        stringSchemaCodec(recordSchema(schema0.asInstanceOf[Schema[Any]], name))
      case s: Schema.Record[_] if s.fields.size == 1 && (name ne null) =>
        stringSchemaCodec(recordSchema(s.asInstanceOf[Schema[Any]], name))
      case s: Schema.Record[_]                                         =>
        stringSchemaCodec(s.asInstanceOf[Schema[Any]])
      case _                                                           =>
        throw new IllegalArgumentException(s"Unsupported schema $schema0")

    }
  }

  private def recordSchema[A](s: Schema[A], name: String): Schema[A] = Schema.CaseClass1[A, A](
    TypeId.Structural,
    Schema.Field(name, s, Chunk.empty, Validation.succeed, identity, (_, v) => v),
    identity,
  )

  private def parsePrimitive(standardType: StandardType[_]): String => Any =
    standardType match {
      case StandardType.UnitType           =>
        val result = ""
        (_: String) => result
      case StandardType.StringType         =>
        (s: String) => s
      case StandardType.BoolType           =>
        (s: String) =>
          s.toLowerCase match {
            case "true" | "on" | "yes" | "1"  => true
            case "false" | "off" | "no" | "0" => false
            case _ => throw DecodeError.ReadError(Cause.fail(new Exception("Invalid boolean value")), s)
          }
      case StandardType.ByteType           =>
        (s: String) =>
          try {
            s.toByte
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.ShortType          =>
        (s: String) =>
          try {
            s.toShort
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.IntType            =>
        (s: String) =>
          try {
            s.toInt
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.LongType           =>
        (s: String) =>
          try {
            s.toLong
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.FloatType          =>
        (s: String) =>
          try {
            s.toFloat
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.DoubleType         =>
        (s: String) =>
          try {
            s.toDouble
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.BinaryType         =>
        val result = DecodeError.UnsupportedSchema(Schema.Primitive(standardType), "TextCodec")
        (_: String) => throw result
      case StandardType.CharType           =>
        (s: String) => s.charAt(0)
      case StandardType.UUIDType           =>
        (s: String) =>
          try {
            UUID.fromString(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.BigDecimalType     =>
        (s: String) =>
          try {
            BigDecimal(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.BigIntegerType     =>
        (s: String) =>
          try {
            BigInt(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.DayOfWeekType      =>
        (s: String) =>
          try {
            DayOfWeek.valueOf(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.MonthType          =>
        (s: String) =>
          try {
            Month.valueOf(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.MonthDayType       =>
        (s: String) =>
          try {
            MonthDay.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.PeriodType         =>
        (s: String) =>
          try {
            Period.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.YearType           =>
        (s: String) =>
          try {
            Year.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.YearMonthType      =>
        (s: String) =>
          try {
            YearMonth.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.ZoneIdType         =>
        (s: String) =>
          try {
            ZoneId.of(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.ZoneOffsetType     =>
        (s: String) =>
          try {
            ZoneOffset.of(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.DurationType       =>
        (s: String) =>
          try {
            java.time.Duration.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.InstantType        =>
        (s: String) =>
          try {
            Instant.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.LocalDateType      =>
        (s: String) =>
          try {
            LocalDate.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.LocalTimeType      =>
        (s: String) =>
          try {
            LocalTime.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.LocalDateTimeType  =>
        (s: String) =>
          try {
            LocalDateTime.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.OffsetTimeType     =>
        (s: String) =>
          try {
            OffsetTime.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.OffsetDateTimeType =>
        (s: String) =>
          try {
            OffsetDateTime.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.ZonedDateTimeType  =>
        (s: String) =>
          try {
            ZonedDateTime.parse(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
      case StandardType.CurrencyType       =>
        (s: String) =>
          try {
            Currency.getInstance(s)
          } catch {
            case e: Exception => throw DecodeError.ReadError(Cause.fail(e), e.getMessage)
          }
    }

  private def camelToKebab(s: String): String =
    if (s.isEmpty) ""
    else if (s.head.isUpper) s.head.toLower.toString + camelToKebab(s.tail)
    else if (s.contains('-')) s
    else
      s.foldLeft("") { (acc, c) =>
        if (c.isUpper) acc + "-" + c.toLower
        else acc + c
      }

  private[http] final case class PrimitiveCodec[A](
    private[http] val schema: Schema[A],
  ) {

    val defaultValue: A =
      StringSchemaCodec.defaultValue(schema)

    private[http] val isOptional: Boolean =
      StringSchemaCodec.isOptional(schema)

    private[http] val isOptionalSchema: Boolean =
      StringSchemaCodec.isOptionalSchema(schema)

    private[http] val encode: A => String =
      PrimitiveCodec.primitiveSchemaEncoder(schema)

    private[http] val decode: String => A =
      PrimitiveCodec.primitiveSchemaDecoder(schema)

  }

  object PrimitiveCodec {

    private[http] def primitiveSchemaDecoder[A](schema: Schema[A]): String => A = schema match {
      case Schema.Optional(schema, _)                =>
        primitiveSchemaDecoder(schema).andThen(Some(_)).asInstanceOf[String => A]
      case Schema.Transform(schema, f, _, _, _)      =>
        primitiveSchemaDecoder(schema).andThen {
          f(_) match {
            case Left(value)  => throw new IllegalArgumentException(value)
            case Right(value) => value
          }
        }.asInstanceOf[String => A]
      case Schema.Primitive(standardType, _)         =>
        parsePrimitive(standardType.asInstanceOf[StandardType[Any]]).asInstanceOf[String => A]
      case Schema.Lazy(schema0)                      =>
        primitiveSchemaDecoder(schema0()).asInstanceOf[String => A]
      case r: Schema.Record[A] if r.fields.size == 1 =>
        val field   = r.fields.head
        val codec   = PrimitiveCodec(field.schema).asInstanceOf[PrimitiveCodec[Any]]
        val decoder = codec.decode.asInstanceOf[String => Any]
        (s: String) =>
          r.construct(Chunk(decoder(s)))(Unsafe.unsafe) match {
            case Left(value)  => throw new IllegalArgumentException(value)
            case Right(value) => value.asInstanceOf[A]
          }
      case _ => throw new IllegalArgumentException(s"Unsupported schema $schema")
    }

    private[http] def primitiveSchemaEncoder[A](schema: Schema[A]): A => String = schema match {
      case Schema.Optional(schema, _)                =>
        val innerEncoder: Any => String = primitiveSchemaEncoder(schema.asInstanceOf[Schema[Any]])
        (a: A) => if (a.isInstanceOf[None.type]) null else innerEncoder(a.asInstanceOf[Some[Any]].get)
      case Schema.Transform(schema, _, g, _, _)      =>
        val innerEncoder: Any => String = primitiveSchemaEncoder(schema.asInstanceOf[Schema[Any]])
        (a: Any) =>
          g.asInstanceOf[Any => Either[String, Any]](a.asInstanceOf[Any]) match {
            case Left(value)  => throw new IllegalArgumentException(value)
            case Right(value) => innerEncoder(value)
          }
      case Schema.Lazy(schema0)                      =>
        primitiveSchemaEncoder(schema0()).asInstanceOf[A => String]
      case Schema.Primitive(_, _)                    =>
        (a: A) => a.toString
      case r: Schema.Record[A] if r.fields.size == 1 =>
        val field   = r.fields.head
        val codec   = PrimitiveCodec(field.schema).asInstanceOf[PrimitiveCodec[Any]]
        val encoder = codec.encode.asInstanceOf[Any => String]
        (a: A) => encoder(field.get(a))
      case _                                         =>
        throw new IllegalArgumentException(s"Unsupported schema $schema")
    }
  }
}
