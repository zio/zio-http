package zio.http.endpoint.openapi

import zio._
import zio.json.ast.Json

import zio.schema.Schema.CaseClass0
import zio.schema._
import zio.schema.annotation._
import zio.schema.codec._
import zio.schema.codec.json._

import zio.http.codec.{SegmentCodec, TextCodec}

private[openapi] case class SerializableJsonSchema(
  @fieldName("$ref") ref: Option[String] = None,
  @fieldName("type") schemaType: Option[TypeOrTypes] = None,
  format: Option[String] = None,
  oneOf: Option[Chunk[SerializableJsonSchema]] = None,
  allOf: Option[Chunk[SerializableJsonSchema]] = None,
  anyOf: Option[Chunk[SerializableJsonSchema]] = None,
  enumValues: Option[Chunk[Json]] = None,
  properties: Option[Map[String, SerializableJsonSchema]] = None,
  additionalProperties: Option[BoolOrSchema] = None,
  required: Option[Chunk[String]] = None,
  items: Option[SerializableJsonSchema] = None,
  nullable: Option[Boolean] = None,
  description: Option[String] = None,
  example: Option[Json] = None,
  examples: Option[Chunk[Json]] = None,
  discriminator: Option[OpenAPI.Discriminator] = None,
  deprecated: Option[Boolean] = None,
  contentEncoding: Option[String] = None,
  contentMediaType: Option[String] = None,
  default: Option[Json] = None,
  pattern: Option[String] = None,
) {
  def asNullableType(nullable: Boolean): SerializableJsonSchema =
    if (nullable && schemaType.isDefined)
      copy(schemaType = Some(schemaType.get.add("null")))
    else if (nullable && oneOf.isDefined)
      copy(oneOf = Some(oneOf.get :+ SerializableJsonSchema(schemaType = Some(TypeOrTypes.Type("null")))))
    else if (nullable && allOf.isDefined)
      SerializableJsonSchema(oneOf =
        Some(Chunk(this, SerializableJsonSchema(schemaType = Some(TypeOrTypes.Type("null"))))),
      )
    else if (nullable && anyOf.isDefined)
      copy(anyOf = Some(anyOf.get :+ SerializableJsonSchema(schemaType = Some(TypeOrTypes.Type("null")))))
    else
      this

}

private[openapi] object SerializableJsonSchema {
  implicit val schema: Schema[SerializableJsonSchema] = DeriveSchema.gen[SerializableJsonSchema]

  val binaryCodec: BinaryCodec[SerializableJsonSchema] =
    JsonCodec.schemaBasedBinaryCodec[SerializableJsonSchema](JsonCodec.Config(ignoreEmptyCollections = true))(
      Schema[SerializableJsonSchema],
    )
}

@noDiscriminator
private[openapi] sealed trait BoolOrSchema

private[openapi] object BoolOrSchema {
  implicit val schema: Schema[BoolOrSchema] = DeriveSchema.gen[BoolOrSchema]

  final case class SchemaWrapper(schema: SerializableJsonSchema) extends BoolOrSchema

  object SchemaWrapper {
    implicit val schema: Schema[SchemaWrapper] =
      Schema[SerializableJsonSchema].transform(SchemaWrapper(_), _.schema)
  }

  final case class BooleanWrapper(value: Boolean) extends BoolOrSchema

  object BooleanWrapper {
    implicit val schema: Schema[BooleanWrapper] =
      Schema[Boolean].transform[BooleanWrapper](b => BooleanWrapper(b), _.value)
  }
}

@noDiscriminator
private[openapi] sealed trait TypeOrTypes { self =>
  def add(value: String): TypeOrTypes =
    self match {
      case TypeOrTypes.Type(string) => TypeOrTypes.Types(Chunk(string, value))
      case TypeOrTypes.Types(chunk) => TypeOrTypes.Types(chunk :+ value)
    }
}

private[openapi] object TypeOrTypes {
  implicit val schema: Schema[TypeOrTypes] = DeriveSchema.gen[TypeOrTypes]

  final case class Type(value: String) extends TypeOrTypes

  object Type {
    implicit val schema: Schema[Type] =
      Schema[String].transform[Type](s => Type(s), _.value)
  }

  final case class Types(value: Chunk[String]) extends TypeOrTypes

  object Types {
    implicit val schema: Schema[Types] =
      Schema.chunk[String].transform[Types](s => Types(s), _.value)
  }
}

final case class JsonSchemas(
  root: JsonSchema,
  rootRef: Option[String],
  children: Map[String, JsonSchema],
)

sealed trait JsonSchema extends Product with Serializable { self =>

  lazy val toJsonBytes: Chunk[Byte] = JsonCodec.schemaBasedBinaryCodec[JsonSchema].encode(self)

  lazy val toJson: String = toJsonBytes.asString

  protected[openapi] def toSerializableSchema: SerializableJsonSchema
  def annotate(annotations: Chunk[JsonSchema.MetaData]): JsonSchema =
    annotations.foldLeft(self) { case (schema, annotation) => schema.annotate(annotation) }
  def annotate(annotation: JsonSchema.MetaData): JsonSchema         =
    JsonSchema.AnnotatedSchema(self, annotation)

  def annotations: Chunk[JsonSchema.MetaData] = self match {
    case JsonSchema.AnnotatedSchema(schema, annotation) => schema.annotations :+ annotation
    case _                                              => Chunk.empty
  }

  def withoutAnnotations: JsonSchema = self match {
    case JsonSchema.AnnotatedSchema(schema, _) => schema.withoutAnnotations
    case _                                     => self
  }

  def examples(examples: Chunk[Json]): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Examples(examples))

  def default(default: Option[Json]): JsonSchema =
    default match {
      case Some(value) => JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Default(value))
      case None        => self
    }

  def default(default: Json): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Default(default))

  def description(description: String): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Description(description))

  def description(description: Option[String]): JsonSchema =
    description match {
      case Some(value) => JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Description(value))
      case None        => self
    }

  def description: Option[String] = self.toSerializableSchema.description

  def nullable(nullable: Boolean): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Nullable(nullable))

  def discriminator(discriminator: OpenAPI.Discriminator): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Discriminator(discriminator))

  def discriminator(discriminator: Option[OpenAPI.Discriminator]): JsonSchema =
    discriminator match {
      case Some(discriminator) =>
        JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Discriminator(discriminator))
      case None                =>
        self
    }

  def deprecated(deprecated: Boolean): JsonSchema =
    if (deprecated) JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Deprecated)
    else self

  def contentEncoding(encoding: JsonSchema.ContentEncoding): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.ContentEncoding(encoding))

  def contentMediaType(mediaType: String): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.ContentMediaType(mediaType))

  def isPrimitive: Boolean = self match {
    case _: JsonSchema.Number  => true
    case _: JsonSchema.Integer => true
    case _: JsonSchema.String  => true
    case JsonSchema.Boolean    => true
    case JsonSchema.Null       => true
    case _                     => false
  }

}

object JsonSchema {

  implicit val schema: Schema[JsonSchema] =
    SerializableJsonSchema.schema.transform[JsonSchema](JsonSchema.fromSerializableSchema, _.toSerializableSchema)

  private[openapi] val codec = JsonCodec.schemaBasedBinaryCodec[JsonSchema]

  private def toJsonAst(schema: Schema[_], v: Any): Json =
    JsonCodec
      .jsonEncoder(schema.asInstanceOf[Schema[Any]])
      .toJsonAST(v)
      .toOption
      .get

  private def fromSerializableSchema(schema: SerializableJsonSchema): JsonSchema = {
    val additionalProperties = schema.additionalProperties match {
      case Some(BoolOrSchema.BooleanWrapper(false)) => Left(false)
      case Some(BoolOrSchema.BooleanWrapper(true))  => Left(true)
      case Some(BoolOrSchema.SchemaWrapper(schema)) => Right(fromSerializableSchema(schema))
      case None                                     => Left(true)
    }

    var jsonSchema: JsonSchema = schema match {
      case schema if schema.ref.isDefined                                                                =>
        RefSchema(schema.ref.get)
      case schema if schema.schemaType.contains(TypeOrTypes.Type("number"))                              =>
        JsonSchema.Number(NumberFormat.fromString(schema.format.getOrElse("double")))
      case schema if schema.schemaType.contains(TypeOrTypes.Type("integer"))                             =>
        JsonSchema.Integer(IntegerFormat.fromString(schema.format.getOrElse("int64")))
      case schema if schema.schemaType.contains(TypeOrTypes.Type("string")) && schema.enumValues.isEmpty =>
        JsonSchema.String(schema.format.map(StringFormat.fromString), schema.pattern.map(Pattern.apply))
      case schema if schema.schemaType.contains(TypeOrTypes.Type("boolean"))                             =>
        JsonSchema.Boolean
      case schema if schema.schemaType.contains(TypeOrTypes.Type("array"))                               =>
        JsonSchema.ArrayType(schema.items.map(fromSerializableSchema))
      case schema if schema.schemaType.contains(TypeOrTypes.Type("object")) || schema.schemaType.isEmpty =>
        JsonSchema.Object(
          schema.properties
            .map(_.map { case (name, schema) => name -> fromSerializableSchema(schema) })
            .getOrElse(Map.empty),
          additionalProperties,
          schema.required.getOrElse(Chunk.empty),
        )
      case schema if schema.enumValues.isDefined                                                         =>
        JsonSchema.Enum(schema.enumValues.get.map(EnumValue.fromJson))
      case schema if schema.oneOf.isDefined                                                              =>
        OneOfSchema(schema.oneOf.get.map(fromSerializableSchema))
      case schema if schema.allOf.isDefined                                                              =>
        AllOfSchema(schema.allOf.get.map(fromSerializableSchema))
      case schema if schema.anyOf.isDefined                                                              =>
        AnyOfSchema(schema.anyOf.get.map(fromSerializableSchema))
      case schema if schema.schemaType.contains(TypeOrTypes.Type("null"))                                =>
        JsonSchema.Null
      case _                                                                                             =>
        throw new IllegalArgumentException(s"Can't convert $schema")
    }

    val examples = Chunk.fromIterable(schema.example) ++ schema.examples.getOrElse(Chunk.empty)
    if (examples.nonEmpty) jsonSchema = jsonSchema.examples(examples)

    schema.description match {
      case Some(value) => jsonSchema = jsonSchema.description(value)
      case None        => ()
    }

    schema.nullable match {
      case Some(value) => jsonSchema = jsonSchema.nullable(value)
      case None        => ()
    }

    schema.discriminator match {
      case Some(value) => jsonSchema = jsonSchema.discriminator(value)
      case None        => ()
    }

    schema.contentEncoding.flatMap(ContentEncoding.fromString) match {
      case Some(value) => jsonSchema = jsonSchema.contentEncoding(value)
      case None        => ()
    }

    schema.contentMediaType match {
      case Some(value) => jsonSchema = jsonSchema.contentMediaType(value)
      case None        => ()
    }

    jsonSchema = jsonSchema.default(schema.default)

    jsonSchema = jsonSchema.deprecated(schema.deprecated.getOrElse(false))

    jsonSchema
  }

  def fromTextCodec(codec: TextCodec[_]): JsonSchema =
    codec match {
      case TextCodec.Constant(string) => JsonSchema.Enum(Chunk(EnumValue.Str(string)))
      case TextCodec.StringCodec      => JsonSchema.String()
      case TextCodec.IntCodec         => JsonSchema.Integer(JsonSchema.IntegerFormat.Int32)
      case TextCodec.LongCodec        => JsonSchema.Integer(JsonSchema.IntegerFormat.Int64)
      case TextCodec.BooleanCodec     => JsonSchema.Boolean
      case TextCodec.UUIDCodec        => JsonSchema.String(JsonSchema.StringFormat.UUID)
    }

  def fromSegmentCodec(codec: SegmentCodec[_]): JsonSchema =
    codec match {
      case SegmentCodec.BoolSeg(_)                    => JsonSchema.Boolean
      case SegmentCodec.IntSeg(_)                     => JsonSchema.Integer(JsonSchema.IntegerFormat.Int32)
      case SegmentCodec.LongSeg(_)                    => JsonSchema.Integer(JsonSchema.IntegerFormat.Int64)
      case SegmentCodec.Text(_)                       => JsonSchema.String()
      case SegmentCodec.UUID(_)                       => JsonSchema.String(JsonSchema.StringFormat.UUID)
      case SegmentCodec.Annotated(codec, annotations) =>
        fromSegmentCodec(codec).description(segmentDoc(annotations)).examples(segmentExamples(codec, annotations))
      case SegmentCodec.Literal(_) => throw new IllegalArgumentException("Literal segment is not supported.")
      case SegmentCodec.Empty      => throw new IllegalArgumentException("Empty segment is not supported.")
      case SegmentCodec.Trailing   => throw new IllegalArgumentException("Trailing segment is not supported.")
    }

  private def segmentDoc(annotations: Chunk[SegmentCodec.MetaData[_]]) =
    annotations.collect { case SegmentCodec.MetaData.Documented(doc) => doc }.reduceOption(_ + _).map(_.toCommonMark)

  private def segmentExamples(codec: SegmentCodec[_], annotations: Chunk[SegmentCodec.MetaData[_]]) =
    Chunk.fromIterable(
      annotations.collect { case SegmentCodec.MetaData.Examples(example) => example.values }.flatten.map { value =>
        codec match {
          case SegmentCodec.Empty           => throw new IllegalArgumentException("Empty segment is not supported.")
          case SegmentCodec.Literal(_)      => throw new IllegalArgumentException("Literal segment is not supported.")
          case SegmentCodec.BoolSeg(_)      => Json.Bool(value.asInstanceOf[Boolean])
          case SegmentCodec.IntSeg(_)       => Json.Num(value.asInstanceOf[Int])
          case SegmentCodec.LongSeg(_)      => Json.Num(value.asInstanceOf[Long])
          case SegmentCodec.Text(_)         => Json.Str(value.asInstanceOf[java.lang.String])
          case SegmentCodec.UUID(_)         => Json.Str(value.asInstanceOf[java.util.UUID].toString)
          case SegmentCodec.Trailing        =>
            throw new IllegalArgumentException("Trailing segment is not supported.")
          case SegmentCodec.Annotated(_, _) =>
            throw new IllegalStateException("Annotated SegmentCodec should never be nested.")
        }
      },
    )

  def fromZSchemaMulti(schema: Schema[_], refType: SchemaStyle = SchemaStyle.Inline): JsonSchemas = {
    val ref = nominal(schema, refType)
    schema match {
      case enum0: Schema.Enum[_] if enum0.cases.forall(_.schema.isInstanceOf[CaseClass0[_]]) =>
        JsonSchemas(fromZSchema(enum0, SchemaStyle.Inline), ref, Map.empty)
      case enum0: Schema.Enum[_]                                                             =>
        JsonSchemas(
          fromZSchema(enum0, SchemaStyle.Inline),
          ref,
          enum0.cases
            .filterNot(_.annotations.exists(_.isInstanceOf[transientCase]))
            .flatMap { c =>
              val key    =
                nominal(c.schema, refType)
                  .orElse(nominal(c.schema, SchemaStyle.Compact))
                  .getOrElse(throw new Exception(s"Unsupported enum case schema: ${c.schema}"))
              val nested = fromZSchemaMulti(
                c.schema,
                refType,
              )
              nested.children + (key -> nested.root)
            }
            .toMap,
        )
      case record: Schema.Record[_]                                                          =>
        val children = record.fields
          .filterNot(_.annotations.exists(_.isInstanceOf[transientField]))
          .flatMap { field =>
            val nested = fromZSchemaMulti(
              field.schema,
              refType,
            )
            nested.rootRef.map(k => nested.children + (k -> nested.root)).getOrElse(nested.children)
          }
          .toMap
        JsonSchemas(fromZSchema(record, SchemaStyle.Inline), ref, children)
      case collection: Schema.Collection[_, _]                                               =>
        collection match {
          case Schema.Sequence(elementSchema, _, _, _, _) =>
            arraySchemaMulti(refType, ref, elementSchema)
          case Schema.Map(_, valueSchema, _)              =>
            val nested = fromZSchemaMulti(valueSchema, refType)
            if (valueSchema.isInstanceOf[Schema.Primitive[_]]) {
              JsonSchemas(
                JsonSchema.Object(
                  Map.empty,
                  Right(nested.root),
                  Chunk.empty,
                ),
                ref,
                nested.children,
              )
            } else {
              JsonSchemas(
                JsonSchema.Object(
                  Map.empty,
                  Right(nested.root),
                  Chunk.empty,
                ),
                ref,
                nested.children + (nested.rootRef.get -> nested.root),
              )
            }
          case Schema.Set(elementSchema, _)               =>
            arraySchemaMulti(refType, ref, elementSchema)
        }
      case Schema.Transform(schema, _, _, _, _)                                              =>
        fromZSchemaMulti(schema, refType)
      case Schema.Primitive(_, _)                                                            =>
        JsonSchemas(fromZSchema(schema, SchemaStyle.Inline), ref, Map.empty)
      case Schema.Optional(schema, _)                                                        =>
        fromZSchemaMulti(schema, refType)
      case Schema.Fail(_, _)                                                                 =>
        throw new IllegalArgumentException("Fail schema is not supported.")
      case Schema.Tuple2(left, right, _)                                                     =>
        val leftSchema  = fromZSchemaMulti(left, refType)
        val rightSchema = fromZSchemaMulti(right, refType)
        JsonSchemas(
          AllOfSchema(Chunk(leftSchema.root, rightSchema.root)),
          ref,
          leftSchema.children ++ rightSchema.children,
        )
      case Schema.Either(left, right, _)                                                     =>
        val leftSchema  = fromZSchemaMulti(left, refType)
        val rightSchema = fromZSchemaMulti(right, refType)
        JsonSchemas(
          OneOfSchema(Chunk(leftSchema.root, rightSchema.root)),
          ref,
          leftSchema.children ++ rightSchema.children,
        )
      case Schema.Lazy(schema0)                                                              =>
        fromZSchemaMulti(schema0(), refType)
      case Schema.Dynamic(_)                                                                 =>
        JsonSchemas(AnyJson, None, Map.empty)
    }
  }

  private def arraySchemaMulti(
    refType: SchemaStyle,
    ref: Option[java.lang.String],
    elementSchema: Schema[_],
  ): JsonSchemas = {
    val nested = fromZSchemaMulti(elementSchema, refType)
    if (elementSchema.isInstanceOf[Schema.Primitive[_]]) {
      JsonSchemas(
        JsonSchema.ArrayType(Some(nested.root)),
        ref,
        nested.children,
      )
    } else {
      JsonSchemas(
        JsonSchema.ArrayType(Some(nested.root)),
        ref,
        nested.children ++ (nested.rootRef.map(_ -> nested.root)),
      )
    }
  }

  def fromZSchema(schema: Schema[_], refType: SchemaStyle = SchemaStyle.Inline): JsonSchema =
    schema match {
      case enum0: Schema.Enum[_] if refType != SchemaStyle.Inline && nominal(enum0).isDefined     =>
        JsonSchema.RefSchema(nominal(enum0, refType).get)
      case enum0: Schema.Enum[_] if enum0.cases.forall(_.schema.isInstanceOf[CaseClass0[_]])      =>
        JsonSchema.Enum(
          enum0.cases.map(c =>
            EnumValue.Str(c.annotations.collectFirst { case caseName(name) => name }.getOrElse(c.id)),
          ),
        )
      case enum0: Schema.Enum[_]                                                                  =>
        val noDiscriminator    = enum0.annotations.exists(_.isInstanceOf[noDiscriminator])
        val discriminatorName0 =
          enum0.annotations.collectFirst { case discriminatorName(name) => name }
        val nonTransientCases  = enum0.cases.filterNot(_.annotations.exists(_.isInstanceOf[transientCase]))
        if (noDiscriminator) {
          JsonSchema
            .OneOfSchema(nonTransientCases.map(c => fromZSchema(c.schema, SchemaStyle.Compact)))
        } else if (discriminatorName0.isDefined) {
          JsonSchema
            .OneOfSchema(nonTransientCases.map(c => fromZSchema(c.schema, SchemaStyle.Compact)))
            .discriminator(
              OpenAPI.Discriminator(
                propertyName = discriminatorName0.get,
                mapping = nonTransientCases.map { c =>
                  val name = c.annotations.collectFirst { case caseName(name) => name }.getOrElse(c.id)
                  name -> nominal(c.schema, refType).orElse(nominal(c.schema, SchemaStyle.Compact)).get
                }.toMap,
              ),
            )
        } else {
          JsonSchema
            .OneOfSchema(nonTransientCases.map { c =>
              val name = c.annotations.collectFirst { case caseName(name) => name }.getOrElse(c.id)
              Object(Map(name -> fromZSchema(c.schema, SchemaStyle.Compact)), Left(false), Chunk(name))
            })
        }
      case record: Schema.Record[_] if refType != SchemaStyle.Inline && nominal(record).isDefined =>
        JsonSchema.RefSchema(nominal(record, refType).get)
      case record: Schema.Record[_]                                                               =>
        val additionalProperties =
          if (record.annotations.exists(_.isInstanceOf[rejectExtraFields])) {
            Left(false)
          } else {
            Left(true)
          }
        val nonTransientFields   =
          record.fields.filterNot(_.annotations.exists(_.isInstanceOf[transientField]))
        JsonSchema
          .Object(
            Map.empty,
            additionalProperties,
            Chunk.empty,
          )
          .addAll(nonTransientFields.map { field =>
            field.name ->
              fromZSchema(field.schema, SchemaStyle.Compact)
                .deprecated(deprecated(field.schema))
                .description(fieldDoc(field))
                .default(fieldDefault(field))
          })
          .required(
            nonTransientFields
              .filterNot(_.schema.isInstanceOf[Schema.Optional[_]])
              .filterNot(_.annotations.exists(_.isInstanceOf[fieldDefaultValue[_]]))
              .filterNot(_.annotations.exists(_.isInstanceOf[optionalField]))
              .map(_.name),
          )
          .deprecated(deprecated(record))
      case collection: Schema.Collection[_, _]                                                    =>
        collection match {
          case Schema.Sequence(elementSchema, _, _, _, _) =>
            JsonSchema.ArrayType(Some(fromZSchema(elementSchema, refType)))
          case Schema.Map(_, valueSchema, _)              =>
            JsonSchema.Object(
              Map.empty,
              Right(fromZSchema(valueSchema, refType)),
              Chunk.empty,
            )
          case Schema.Set(elementSchema, _)               =>
            JsonSchema.ArrayType(Some(fromZSchema(elementSchema, refType)))
        }
      case Schema.Transform(schema, _, _, _, _)                                                   =>
        fromZSchema(schema, refType)
      case Schema.Primitive(standardType, _)                                                      =>
        standardType match {
          case StandardType.UnitType           => JsonSchema.Null
          case StandardType.StringType         => JsonSchema.String()
          case StandardType.BoolType           => JsonSchema.Boolean
          case StandardType.ByteType           => JsonSchema.String()
          case StandardType.ShortType          => JsonSchema.Integer(IntegerFormat.Int32)
          case StandardType.IntType            => JsonSchema.Integer(IntegerFormat.Int32)
          case StandardType.LongType           => JsonSchema.Integer(IntegerFormat.Int64)
          case StandardType.FloatType          => JsonSchema.Number(NumberFormat.Float)
          case StandardType.DoubleType         => JsonSchema.Number(NumberFormat.Double)
          case StandardType.BinaryType         => JsonSchema.String()
          case StandardType.CharType           => JsonSchema.String()
          case StandardType.UUIDType           => JsonSchema.String(StringFormat.UUID)
          case StandardType.BigDecimalType     => JsonSchema.Number(NumberFormat.Double) // TODO: Is this correct?
          case StandardType.BigIntegerType     => JsonSchema.Integer(IntegerFormat.Int64)
          case StandardType.DayOfWeekType      => JsonSchema.String()
          case StandardType.MonthType          => JsonSchema.String()
          case StandardType.MonthDayType       => JsonSchema.String()
          case StandardType.PeriodType         => JsonSchema.String()
          case StandardType.YearType           => JsonSchema.String()
          case StandardType.YearMonthType      => JsonSchema.String()
          case StandardType.ZoneIdType         => JsonSchema.String()
          case StandardType.ZoneOffsetType     => JsonSchema.String()
          case StandardType.DurationType       => JsonSchema.String(StringFormat.Duration)
          case StandardType.InstantType        => JsonSchema.String()
          case StandardType.LocalDateType      => JsonSchema.String()
          case StandardType.LocalTimeType      => JsonSchema.String()
          case StandardType.LocalDateTimeType  => JsonSchema.String()
          case StandardType.OffsetTimeType     => JsonSchema.String()
          case StandardType.OffsetDateTimeType => JsonSchema.String()
          case StandardType.ZonedDateTimeType  => JsonSchema.String()
        }

      case Schema.Optional(schema, _)    => fromZSchema(schema, refType).nullable(true)
      case Schema.Fail(_, _)             => throw new IllegalArgumentException("Fail schema is not supported.")
      case Schema.Tuple2(left, right, _) => AllOfSchema(Chunk(fromZSchema(left, refType), fromZSchema(right, refType)))
      case Schema.Either(left, right, _) => OneOfSchema(Chunk(fromZSchema(left, refType), fromZSchema(right, refType)))
      case Schema.Lazy(schema0)          => fromZSchema(schema0(), refType)
      case Schema.Dynamic(_)             => AnyJson

    }

  sealed trait SchemaStyle extends Product with Serializable
  object SchemaStyle {

    /** Generates inline json schema */
    case object Inline extends SchemaStyle

    /**
     * Generates references to json schemas under #/components/schemas/{schema}
     * and uses the full package path to help to generate unique schema names.
     * @see
     *   SchemaStyle.Compact for compact schema names.
     */
    case object Reference extends SchemaStyle

    /**
     * Generates references to json schemas under #/components/schemas/{schema}
     * and uses the type name to help to generate schema names.
     * @see
     *   SchemaStyle.Reference for full package path schema names to avoid name
     *   collisions.
     */
    case object Compact extends SchemaStyle
  }

  private def deprecated(schema: Schema[_]): Boolean =
    schema.annotations.exists(_.isInstanceOf[scala.deprecated])

  private def fieldDoc(schema: Schema.Field[_, _]): Option[java.lang.String] = {
    val description0 = schema.annotations.collectFirst { case description(value) => value }
    val defaultValue = schema.annotations.collectFirst { case fieldDefaultValue(value) => value }.map { _ =>
      s"${if (description0.isDefined) "\n" else ""}If not set, this field defaults to the value of the default annotation."
    }
    Some(description0.getOrElse("") + defaultValue.getOrElse(""))
      .filter(_.nonEmpty)
  }

  private def fieldDefault(schema: Schema.Field[_, _]): Option[Json] =
    schema.annotations.collectFirst { case fieldDefaultValue(value) => value }
      .map(toJsonAst(schema.schema, _))

  private def nominal(schema: Schema[_], referenceType: SchemaStyle = SchemaStyle.Reference): Option[java.lang.String] =
    schema match {
      case enumSchema: Schema.Enum[_] => refForTypeId(enumSchema.id, referenceType)
      case record: Schema.Record[_]   => refForTypeId(record.id, referenceType)
      case _                          => None
    }

  private def refForTypeId(id: TypeId, referenceType: SchemaStyle): Option[java.lang.String] =
    id match {
      case nominal: TypeId.Nominal if referenceType == SchemaStyle.Reference =>
        Some(s"#/components/schemas/${nominal.fullyQualified.replace(".", "_")}")
      case nominal: TypeId.Nominal if referenceType == SchemaStyle.Compact   =>
        Some(s"#/components/schemas/${nominal.typeName}")
      case _                                                                 =>
        None
    }

  def obj(properties: (java.lang.String, JsonSchema)*): JsonSchema =
    JsonSchema.Object(
      properties = properties.toMap,
      additionalProperties = Left(false),
      required = Chunk.fromIterable(properties.toMap.keys),
    )

  final case class AnnotatedSchema(schema: JsonSchema, annotation: MetaData) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema = {
      annotation match {
        case MetaData.Examples(chunk)              =>
          schema.toSerializableSchema.copy(examples = Some(chunk))
        case MetaData.Discriminator(discriminator) =>
          schema.toSerializableSchema.copy(discriminator = Some(discriminator))
        case MetaData.Nullable(nullable)           =>
          schema.toSerializableSchema.asNullableType(nullable)
        case MetaData.Description(description)     =>
          schema.toSerializableSchema.copy(description = Some(description))
        case MetaData.ContentEncoding(encoding)    =>
          schema.toSerializableSchema.copy(contentEncoding = Some(encoding.productPrefix.toLowerCase))
        case MetaData.ContentMediaType(mediaType)  =>
          schema.toSerializableSchema.copy(contentMediaType = Some(mediaType))
        case MetaData.Deprecated                   =>
          schema.toSerializableSchema.copy(deprecated = Some(true))
        case MetaData.Default(default)             =>
          schema.toSerializableSchema.copy(default = Some(default))
      }
    }
  }

  sealed trait MetaData extends Product with Serializable
  object MetaData {
    final case class Examples(chunk: Chunk[Json])                          extends MetaData
    final case class Default(default: Json)                                extends MetaData
    final case class Discriminator(discriminator: OpenAPI.Discriminator)   extends MetaData
    final case class Nullable(nullable: Boolean)                           extends MetaData
    final case class Description(description: java.lang.String)            extends MetaData
    final case class ContentEncoding(encoding: JsonSchema.ContentEncoding) extends MetaData
    final case class ContentMediaType(mediaType: java.lang.String)         extends MetaData
    case object Deprecated                                                 extends MetaData
  }

  sealed trait ContentEncoding extends Product with Serializable
  object ContentEncoding {
    case object SevenBit        extends ContentEncoding
    case object EightBit        extends ContentEncoding
    case object Binary          extends ContentEncoding
    case object QuotedPrintable extends ContentEncoding
    case object Base16          extends ContentEncoding
    case object Base32          extends ContentEncoding
    case object Base64          extends ContentEncoding

    def fromString(string: java.lang.String): Option[ContentEncoding] =
      string.toLowerCase match {
        case "7bit"         => Some(SevenBit)
        case "8bit"         => Some(EightBit)
        case "binary"       => Some(Binary)
        case "quoted-print" => Some(QuotedPrintable)
        case "base16"       => Some(Base16)
        case "base32"       => Some(Base32)
        case "base64"       => Some(Base64)
        case _              => None
      }
  }

  final case class RefSchema(ref: java.lang.String) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(ref = Some(ref))
  }

  final case class OneOfSchema(oneOf: Chunk[JsonSchema]) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        oneOf = Some(oneOf.map(_.toSerializableSchema)),
      )
  }

  final case class AllOfSchema(allOf: Chunk[JsonSchema]) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        allOf = Some(allOf.map(_.toSerializableSchema)),
      )
  }

  final case class AnyOfSchema(anyOf: Chunk[JsonSchema]) extends JsonSchema {
    def minify: JsonSchema = {
      val (objects, others) = anyOf.distinct.span(_.withoutAnnotations.isInstanceOf[JsonSchema.Object])
      val markedForRemoval  = (for {
        obj      <- objects
        otherObj <- objects
        notNullableSchemas = obj.withoutAnnotations.asInstanceOf[JsonSchema.Object].properties.collect {
          case (name, schema)
              if !schema.annotations.exists { case MetaData.Nullable(nullable) => nullable; case _ => false } =>
            name -> schema
        }
        if notNullableSchemas == otherObj.withoutAnnotations.asInstanceOf[JsonSchema.Object].properties
      } yield otherObj).distinct

      val minified = objects.filterNot(markedForRemoval.contains).map { obj =>
        val annotations        = obj.annotations
        val asObject           = obj.withoutAnnotations.asInstanceOf[JsonSchema.Object]
        val notNullableSchemas = asObject.properties.collect {
          case (name, schema)
              if !schema.annotations.exists { case MetaData.Nullable(nullable) => nullable; case _ => false } =>
            name -> schema
        }
        asObject.required(asObject.required.filter(notNullableSchemas.contains)).annotate(annotations)
      }
      val newAnyOf = minified ++ others

      if (newAnyOf.size == 1) newAnyOf.head else AnyOfSchema(newAnyOf)
    }

    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        anyOf = Some(anyOf.map(_.toSerializableSchema)),
      )
  }

  final case class Number(format: NumberFormat) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("number")),
        format = Some(format.productPrefix.toLowerCase),
      )
  }

  sealed trait NumberFormat extends Product with Serializable
  object NumberFormat {

    def fromString(string: java.lang.String): NumberFormat =
      string match {
        case "float"  => Float
        case "double" => Double
        case _        => throw new IllegalArgumentException(s"Unknown number format: $string")
      }
    case object Float extends NumberFormat
    case object Double extends NumberFormat

  }

  final case class Integer(format: IntegerFormat) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("integer")),
        format = Some(format.productPrefix.toLowerCase),
      )
  }

  sealed trait IntegerFormat extends Product with Serializable
  object IntegerFormat {

    def fromString(string: java.lang.String): IntegerFormat =
      string match {
        case "int32"     => Int32
        case "int64"     => Int64
        case "timestamp" => Timestamp
        case _           => throw new IllegalArgumentException(s"Unknown integer format: $string")
      }
    case object Int32 extends IntegerFormat
    case object Int64     extends IntegerFormat
    case object Timestamp extends IntegerFormat
  }

  // TODO: Add string formats and patterns
  final case class String(format: Option[StringFormat], pattern: Option[Pattern]) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("string")),
        format = format.map(_.value),
        pattern = pattern.map(_.value),
      )
  }

  object String {
    def apply(format: StringFormat): String = String(Some(format), None)
    def apply(pattern: Pattern): String     = String(None, Some(pattern))
    def apply(): String                     = String(None, None)
  }

  sealed trait StringFormat extends Product with Serializable {
    def value: java.lang.String
  }

  object StringFormat {
    case class Custom(value: java.lang.String) extends StringFormat
    case object Date                           extends StringFormat { val value = "date"                  }
    case object DateTime                       extends StringFormat { val value = "date-time"             }
    case object Duration                       extends StringFormat { val value = "duration"              }
    case object Email                          extends StringFormat { val value = "email"                 }
    case object Hostname                       extends StringFormat { val value = "hostname"              }
    case object IdnEmail                       extends StringFormat { val value = "idn-email"             }
    case object IdnHostname                    extends StringFormat { val value = "idn-hostname"          }
    case object IPv4                           extends StringFormat { val value = "ipv4"                  }
    case object IPv6                           extends StringFormat { val value = "ipv6"                  }
    case object IRI                            extends StringFormat { val value = "iri"                   }
    case object IRIReference                   extends StringFormat { val value = "iri-reference"         }
    case object JSONPointer                    extends StringFormat { val value = "json-pointer"          }
    case object Password                       extends StringFormat { val value = "password"              }
    case object Regex                          extends StringFormat { val value = "regex"                 }
    case object RelativeJSONPointer            extends StringFormat { val value = "relative-json-pointer" }
    case object Time                           extends StringFormat { val value = "time"                  }
    case object URI                            extends StringFormat { val value = "uri"                   }
    case object URIRef                         extends StringFormat { val value = "uri-reference"         }
    case object URITemplate                    extends StringFormat { val value = "uri-template"          }
    case object UUID                           extends StringFormat { val value = "uuid"                  }

    def fromString(string: java.lang.String): StringFormat =
      string match {
        case "date"                  => Date
        case "date-time"             => DateTime
        case "duration"              => Duration
        case "email"                 => Email
        case "hostname"              => Hostname
        case "idn-email"             => IdnEmail
        case "idn-hostname"          => IdnHostname
        case "ipv4"                  => IPv4
        case "ipv6"                  => IPv6
        case "iri"                   => IRI
        case "iri-reference"         => IRIReference
        case "json-pointer"          => JSONPointer
        case "password"              => Password
        case "regex"                 => Regex
        case "relative-json-pointer" => RelativeJSONPointer
        case "time"                  => Time
        case "uri"                   => URI
        case "uri-reference"         => URIRef
        case "uri-template"          => URITemplate
        case "uuid"                  => UUID
        case value                   => Custom(value)
      }
  }

  final case class Pattern(value: java.lang.String) extends Product with Serializable

  case object Boolean extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(schemaType = Some(TypeOrTypes.Type("boolean")))
  }

  final case class ArrayType(items: Option[JsonSchema]) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("array")),
        items = items.map(_.toSerializableSchema),
      )
  }

  final case class Object(
    properties: Map[java.lang.String, JsonSchema],
    additionalProperties: Either[Boolean, JsonSchema],
    required: Chunk[java.lang.String],
  ) extends JsonSchema {
    def addAll(value: Chunk[(java.lang.String, JsonSchema)]): Object =
      value.foldLeft(this) { case (obj, (name, schema)) =>
        schema match {
          case Object(properties, additionalProperties, required) =>
            obj.copy(
              properties = obj.properties ++ properties,
              additionalProperties = combineAdditionalProperties(obj.additionalProperties, additionalProperties),
              required = obj.required ++ required,
            )
          case schema => obj.copy(properties = obj.properties + (name -> schema))
        }
      }

    def required(required: Chunk[java.lang.String]): Object =
      this.copy(required = required)

    private def combineAdditionalProperties(
      left: Either[Boolean, JsonSchema],
      right: Either[Boolean, JsonSchema],
    ): Either[Boolean, JsonSchema] =
      (left, right) match {
        case (Left(false), _)            => Left(false)
        case (_, Left(_))                => left
        case (Left(true), _)             => right
        case (Right(left), Right(right)) =>
          Right(AllOfSchema(Chunk(left, right)))
      }

    override protected[openapi] def toSerializableSchema: SerializableJsonSchema = {
      val additionalProperties = this.additionalProperties match {
        case Left(true)    => Some(BoolOrSchema.BooleanWrapper(true))
        case Left(false)   => Some(BoolOrSchema.BooleanWrapper(false))
        case Right(schema) => Some(BoolOrSchema.SchemaWrapper(schema.toSerializableSchema))
      }
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("object")),
        properties = Some(properties.map { case (name, schema) => name -> schema.toSerializableSchema }),
        additionalProperties = additionalProperties,
        required = if (required.isEmpty) None else Some(required),
      )
    }
  }

  object Object {
    val empty: JsonSchema.Object = JsonSchema.Object(Map.empty, Left(true), Chunk.empty)
  }

  final case class Enum(values: Chunk[EnumValue]) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("string")),
        enumValues = Some(values.map(_.toJson)),
      )
  }

  @noDiscriminator
  sealed trait EnumValue { self =>
    def toJson: Json = self match {
      case EnumValue.Bool(value)        => Json.Bool(value)
      case EnumValue.Str(value)         => Json.Str(value)
      case EnumValue.Num(value)         => Json.Num(value)
      case EnumValue.Null               => Json.Null
      case EnumValue.SchemaValue(value) =>
        Json.decoder
          .decodeJson(value.toJson)
          .getOrElse(throw new IllegalArgumentException(s"Can't convert $self"))
    }
  }

  object EnumValue {

    def fromJson(json: Json): EnumValue =
      json match {
        case Json.Str(value)  => Str(value)
        case Json.Num(value)  => Num(value)
        case Json.Bool(value) => Bool(value)
        case Json.Null        => Null
        case other            =>
          SchemaValue(
            JsonSchema.codec
              .decode(Chunk.fromArray(other.toString().getBytes))
              .getOrElse(throw new IllegalArgumentException(s"Can't convert $json")),
          )
      }

    final case class SchemaValue(value: JsonSchema) extends EnumValue
    final case class Bool(value: Boolean)           extends EnumValue
    final case class Str(value: java.lang.String)   extends EnumValue
    final case class Num(value: BigDecimal)         extends EnumValue
    case object Null                                extends EnumValue

  }

  case object Null extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("null")),
      )
  }

  case object AnyJson extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema()
  }

}
