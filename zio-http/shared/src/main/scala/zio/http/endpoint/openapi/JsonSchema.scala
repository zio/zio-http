package zio.http.endpoint.openapi

import scala.annotation.{nowarn, tailrec}

import zio._
import zio.json.ast.Json

import zio.schema.Schema.CaseClass0
import zio.schema._
import zio.schema.annotation._
import zio.schema.codec.JsonCodec.ExplicitConfig
import zio.schema.codec._
import zio.schema.codec.json._
import zio.schema.validation._

import zio.http.codec._
import zio.http.endpoint.openapi.JsonSchema.MetaData

@nowarn("msg=possible missing interpolator")
private[openapi] case class SerializableJsonSchema(
  @fieldName("$ref") ref: Option[String] = None,
  @fieldName("type") schemaType: Option[TypeOrTypes] = None,
  format: Option[String] = None,
  oneOf: Option[Chunk[SerializableJsonSchema]] = None,
  allOf: Option[Chunk[SerializableJsonSchema]] = None,
  anyOf: Option[Chunk[SerializableJsonSchema]] = None,
  @fieldName("enum") enumValues: Option[Chunk[Json]] = None,
  properties: Option[Map[String, SerializableJsonSchema]] = None,
  additionalProperties: Option[BoolOrSchema] = None,
  @fieldName("x-string-key-schema") optionalKeySchema: Option[SerializableJsonSchema] = None,
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
  minLength: Option[Int] = None,
  maxLength: Option[Int] = None,
  minimum: Option[Either[Double, Long]] = None,
  maximum: Option[Either[Double, Long]] = None,
  multipleOf: Option[Double] = None,
  exclusiveMinimum: Option[Either[Boolean, Either[Double, Long]]] = None,
  exclusiveMaximum: Option[Either[Boolean, Either[Double, Long]]] = None,
  uniqueItems: Option[Boolean] = None,
  minItems: Option[Int] = None,
) {
  def asNullableType(nullable: Boolean): SerializableJsonSchema = {
    import SerializableJsonSchema.typeNull

    if (nullable && schemaType.isDefined)
      copy(schemaType = Some(schemaType.get.add("null")))
    else if (nullable && oneOf.isDefined)
      copy(oneOf = Some((oneOf.get :+ typeNull).distinct))
    else if (nullable && allOf.isDefined)
      SerializableJsonSchema(allOf = Some((allOf.get :+ typeNull).distinct))
    else if (nullable && anyOf.isDefined)
      copy(anyOf = Some((anyOf.get :+ typeNull).distinct))
    else if (nullable && ref.isDefined)
      SerializableJsonSchema(anyOf = Some(Chunk(typeNull, this)))
    else
      this
  }
}

private[openapi] object SerializableJsonSchema {

  /**
   * Used to generate a OpenAPI schema part looking like this:
   * {{{
   *   { "type": "null"}
   * }}}
   */
  private[SerializableJsonSchema] val typeNull: SerializableJsonSchema =
    SerializableJsonSchema(schemaType = Some(TypeOrTypes.Type("null")))

  implicit val doubleOrLongSchema: Schema[Either[Double, Long]] =
    Schema.fallback(Schema[Double], Schema[Long]).transform(_.toEither, Fallback.fromEither)

  implicit val eitherBooleanDoubleOrLongSchema: Schema[Either[Boolean, Either[Double, Long]]] =
    Schema.fallback(Schema[Boolean], doubleOrLongSchema).transform(_.toEither, Fallback.fromEither)

  implicit val schema: Schema[SerializableJsonSchema] = DeriveSchema.gen[SerializableJsonSchema]

  val binaryCodec: BinaryCodec[SerializableJsonSchema] =
    JsonCodec.schemaBasedBinaryCodec[SerializableJsonSchema](
      JsonCodec.Configuration(
        explicitEmptyCollections = ExplicitConfig(encoding = false),
        explicitNulls = ExplicitConfig(encoding = false),
      ),
    )(
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
      Schema[SerializableJsonSchema].transform(SchemaWrapper.apply, _.schema)
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
      case TypeOrTypes.Type(string) => TypeOrTypes.Types(Chunk(string, value).distinct)
      case TypeOrTypes.Types(chunk) => TypeOrTypes.Types((chunk :+ value).distinct)
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

  def toJsonBytes: Chunk[Byte] = JsonCodec.schemaBasedBinaryCodec[JsonSchema].encode(self)

  def toJson: String = toJsonBytes.asString

  protected[openapi] def toSerializableSchema: SerializableJsonSchema
  def annotate(annotations: Chunk[JsonSchema.MetaData]): JsonSchema =
    annotations.foldLeft(self) { case (schema, annotation) => schema.annotate(annotation) }
  def annotate(annotation: JsonSchema.MetaData): JsonSchema         =
    JsonSchema.AnnotatedSchema(self, annotation)

  def annotations: Chunk[JsonSchema.MetaData] = self match {
    case JsonSchema.AnnotatedSchema(schema, annotation) => schema.annotations :+ annotation
    case _                                              => Chunk.empty
  }

  final def isNullable: Boolean =
    annotations.exists {
      case MetaData.Nullable(nullable) => nullable
      case _                           => false
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
    if (nullable) JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Nullable(true))
    else self

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

  def isCollection: Boolean = self match {
    case _: JsonSchema.ArrayType => true
    case obj: JsonSchema.Object  => obj.properties.isEmpty && obj.additionalProperties.isRight
    case _                       => false
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

  def fromTextCodec(codec: TextCodec[_]): JsonSchema =
    codec match {
      case TextCodec.Constant(string) => JsonSchema.Enum(Chunk(EnumValue.Str(string)))
      case TextCodec.StringCodec      => JsonSchema.String()
      case TextCodec.IntCodec         => JsonSchema.Integer(JsonSchema.IntegerFormat.Int32)
      case TextCodec.LongCodec        => JsonSchema.Integer(JsonSchema.IntegerFormat.Int64)
      case TextCodec.BooleanCodec     => JsonSchema.Boolean
      case TextCodec.UUIDCodec        => JsonSchema.String(JsonSchema.StringFormat.UUID)
    }

  private[openapi] def fromSerializableSchema(schema: SerializableJsonSchema): JsonSchema = {

    val definedAttributesCount = schema.productIterator.count(_.asInstanceOf[Option[_]].isDefined)

    // if type: object with additionalProperties defined,
    // but nothing else, we should assume a free form object
    // if type is not defined, but additionalProperties is,
    // and nothing else, object is assumed again.
    // if both type: object and additionalProperties are defined,
    // and nothing else, object is assumed.
    def anyObject: Boolean = {
      val isObject = schema.schemaType.contains(TypeOrTypes.Type("object"))
      val hasAttrs = schema.additionalProperties.collect { case BoolOrSchema.BooleanWrapper(b) =>
        b
      }.exists(identity)

      // if definedAttributesCount == 0, this also yields true,
      // but we check for it before calling this function,
      // thus no need to check it here.
      val isAnyObj = List(isObject, hasAttrs).count(identity) == definedAttributesCount

      isAnyObj
    }

    if (definedAttributesCount == 0) JsonSchema.AnyJson
    else if (anyObject) JsonSchema.Object(Map.empty, Right(JsonSchema.AnyJson), Chunk.empty)
    else {

      val additionalProperties = schema.additionalProperties match {
        case Some(BoolOrSchema.BooleanWrapper(bool))  => Left(bool)
        case Some(BoolOrSchema.SchemaWrapper(schema)) =>
          val valuesSchema = fromSerializableSchema(schema)
          Right(
            schema.optionalKeySchema.fold(valuesSchema)(keySchema =>
              valuesSchema.annotate(
                MetaData.KeySchema(
                  fromSerializableSchema(keySchema),
                ),
              ),
            ),
          )
        case None                                     => Left(true)
      }

      var jsonSchema: JsonSchema = schema match {
        case schema if schema.ref.isDefined                                                                =>
          RefSchema(schema.ref.get)
        case schema if schema.schemaType.contains(TypeOrTypes.Type("number"))                              =>
          JsonSchema.Number(
            NumberFormat.fromString(schema.format.getOrElse("double")),
            schema.minimum.map(_.fold(identity, _.toDouble)),
            schema.exclusiveMinimum.map(_.map(_.fold(identity, _.toDouble))),
            schema.maximum.map(_.fold(identity, _.toDouble)),
            schema.exclusiveMaximum.map(_.map(_.fold(identity, _.toDouble))),
          )
        case schema if schema.schemaType.contains(TypeOrTypes.Type("integer"))                             =>
          JsonSchema.Integer(
            IntegerFormat.fromString(schema.format.getOrElse("int64")),
            schema.minimum.map(_.fold(_.toLong, identity)),
            schema.exclusiveMinimum.map(_.map(_.fold(_.toLong, identity))),
            schema.maximum.map(_.fold(_.toLong, identity)),
            schema.exclusiveMaximum.map(_.map(_.fold(_.toLong, identity))),
          )
        case schema if schema.schemaType.contains(TypeOrTypes.Type("string")) && schema.enumValues.isEmpty =>
          JsonSchema.String(
            schema.format.map(StringFormat.fromString),
            schema.pattern.map(Pattern.apply),
            schema.maxLength,
            schema.minLength,
          )
        case schema if schema.schemaType.contains(TypeOrTypes.Type("boolean"))                             =>
          JsonSchema.Boolean
        case schema if schema.schemaType.contains(TypeOrTypes.Type("array"))                               =>
          JsonSchema.ArrayType(
            schema.items.map(fromSerializableSchema),
            schema.minItems,
            schema.uniqueItems.contains(true),
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
        case schema if schema.schemaType.contains(TypeOrTypes.Type("object")) || schema.schemaType.isEmpty =>
          JsonSchema.Object(
            schema.properties
              .map(_.map { case (name, schema) => name -> fromSerializableSchema(schema) })
              .getOrElse(Map.empty),
            additionalProperties,
            schema.required.getOrElse(Chunk.empty),
          )
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
  }

  def fromSegmentCodec(codec: SegmentCodec[_]): JsonSchema =
    codec match {
      case SegmentCodec.BoolSeg(_)        => JsonSchema.Boolean
      case SegmentCodec.IntSeg(_)         => JsonSchema.Integer(JsonSchema.IntegerFormat.Int32)
      case SegmentCodec.LongSeg(_)        => JsonSchema.Integer(JsonSchema.IntegerFormat.Int64)
      case SegmentCodec.Text(_)           => JsonSchema.String()
      case SegmentCodec.UUID(_)           => JsonSchema.String(JsonSchema.StringFormat.UUID)
      case SegmentCodec.Literal(_)        => throw new IllegalArgumentException("Literal segment is not supported.")
      case SegmentCodec.Empty             => throw new IllegalArgumentException("Empty segment is not supported.")
      case SegmentCodec.Trailing          => throw new IllegalArgumentException("Trailing segment is not supported.")
      case SegmentCodec.Combined(_, _, _) => throw new IllegalArgumentException("Combined segment is not supported.")
    }

  def fromZSchemaMulti(
    schema: Schema[_],
    refType: SchemaStyle = SchemaStyle.Inline,
    seen: Set[java.lang.String] = Set.empty,
  ): JsonSchemas = {
    val ref = nominal(schema, refType)
    if (ref.exists(seen.contains)) {
      JsonSchemas(RefSchema(ref.get), ref, Map.empty)
    } else {
      val seenWithCurrent = seen ++ ref
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
                val nested = fromZSchemaMulti(
                  c.schema,
                  refType,
                  seenWithCurrent,
                )
                nested.children ++ key.map(_ -> nested.root)
              }
              .toMap,
          )
        case record: Schema.Record[_]                                                          =>
          val children = record.fields
            .filterNot(_.annotations.exists(_.isInstanceOf[transientField]))
            .flatMap { field =>
              val nested = fromZSchemaMulti(
                field.annotations.foldLeft(field.schema)((schema, annotation) => schema.annotate(annotation)),
                refType,
                seenWithCurrent,
              )
              nested.rootRef.fold(ifEmpty = nested.children)(k => nested.children + (k -> nested.root))
            }
            .toMap
          JsonSchemas(fromZSchema(record, SchemaStyle.Inline), ref, children)
        case collection: Schema.Collection[_, _]                                               =>
          collection match {
            case Schema.Sequence(elementSchema, _, _, _, _)                =>
              arraySchemaMulti(refType, ref, elementSchema, seenWithCurrent)
            case Schema.NonEmptySequence(elementSchema, _, _, _, identity) =>
              arraySchemaMulti(
                refType,
                ref,
                elementSchema,
                seenWithCurrent,
                minItems = Some(1),
                uniqueItems = identity == "NonEmptySet",
              )
            case Schema.Map(keySchema, valueSchema, _)                     =>
              mapSchema(refType, ref, seenWithCurrent, keySchema, valueSchema)
            case Schema.NonEmptyMap(keySchema, valueSchema, _)             =>
              mapSchema(refType, ref, seenWithCurrent, keySchema, valueSchema)
            case Schema.Set(elementSchema, _)                              =>
              arraySchemaMulti(refType, ref, elementSchema, seenWithCurrent)
          }
        case Schema.Transform(schema, _, _, _, _)                                              =>
          fromZSchemaMulti(schema, refType, seen)
        case Schema.Primitive(_, _)                                                            =>
          JsonSchemas(fromZSchema(schema, SchemaStyle.Inline), ref, Map.empty)
        case Schema.Optional(schema, _)                                                        =>
          fromZSchemaMulti(schema, refType, seenWithCurrent)
        case Schema.Fail(_, _)                                                                 =>
          throw new IllegalArgumentException("Fail schema is not supported.")
        case Schema.Tuple2(left, right, _)                                                     =>
          val leftSchema  = fromZSchemaMulti(left, refType, seenWithCurrent)
          val rightSchema = fromZSchemaMulti(right, refType, seenWithCurrent)
          JsonSchemas(
            AllOfSchema(Chunk(leftSchema.root, rightSchema.root)),
            ref,
            leftSchema.children ++ rightSchema.children,
          )
        case Schema.Either(left, right, _)                                                     =>
          val leftSchema  = fromZSchemaMulti(left, refType, seenWithCurrent)
          val rightSchema = fromZSchemaMulti(right, refType, seenWithCurrent)
          JsonSchemas(
            OneOfSchema(Chunk(leftSchema.root, rightSchema.root)),
            ref,
            leftSchema.children ++ rightSchema.children,
          )
        case Schema.Fallback(left, right, fullDecode, _)                                       =>
          val leftSchema  = fromZSchemaMulti(left, refType, seenWithCurrent)
          val rightSchema = fromZSchemaMulti(right, refType, seenWithCurrent)
          val candidates  =
            if (fullDecode)
              Chunk(
                AllOfSchema(Chunk(leftSchema.root, rightSchema.root)),
                leftSchema.root,
                rightSchema.root,
              )
            else
              Chunk(
                leftSchema.root,
                rightSchema.root,
              )

          JsonSchemas(
            OneOfSchema(candidates),
            ref,
            leftSchema.children ++ rightSchema.children,
          )
        case Schema.Lazy(schema0)                                                              =>
          fromZSchemaMulti(schema0(), refType, seen)
        case Schema.Dynamic(_)                                                                 =>
          JsonSchemas(AnyJson, None, Map.empty)
      }
    }
  }

  private def mapSchema[K, V](
    refType: SchemaStyle,
    ref: Option[java.lang.String],
    seenWithCurrent: Set[java.lang.String],
    keySchema: Schema[K],
    valueSchema: Schema[V],
  ) = {
    val nested          = fromZSchemaMulti(valueSchema, refType, seenWithCurrent)
    val mapObjectSchema = annotateMapSchemaWithKeysSchema(nested.root, keySchema)

    if (valueSchema.isInstanceOf[Schema.Primitive[_]]) {
      JsonSchemas(
        mapObjectSchema,
        ref,
        nested.children,
      )
    } else {
      JsonSchemas(
        mapObjectSchema,
        ref,
        nested.children ++ nested.rootRef.map(_ -> nested.root),
      )
    }
  }

  private def arraySchemaMulti(
    refType: SchemaStyle,
    ref: Option[java.lang.String],
    elementSchema: Schema[_],
    seen: Set[java.lang.String],
    minItems: Option[Int] = None,
    uniqueItems: Boolean = false,
  ): JsonSchemas = {
    val nested = fromZSchemaMulti(elementSchema, refType, seen)
    if (elementSchema.isInstanceOf[Schema.Primitive[_]]) {
      JsonSchemas(
        JsonSchema.ArrayType(Some(nested.root), minItems, uniqueItems),
        ref,
        nested.children,
      )
    } else {
      JsonSchemas(
        JsonSchema.ArrayType(Some(nested.root), minItems, uniqueItems),
        ref,
        nested.children ++ nested.rootRef.map(_ -> nested.root),
      )
    }
  }

  private def annotationForKeySchema[K](keySchema: Schema[K]): Option[MetaData.KeySchema] =
    keySchema match {
      case Schema.Primitive(StandardType.StringType, annotations) if annotations.isEmpty => None
      case nonSimple                                                                     =>
        fromZSchema(nonSimple) match {
          case JsonSchema.String(None, None, None, None) => None // no need for extension
          case s: JsonSchema.String                      => Some(MetaData.KeySchema(s))
          case _                                         => None // only string keys are allowed
        }
    }

  private def annotateMapSchemaWithKeysSchema[K](valueSchema: JsonSchema, keySchema: Schema[K]) = {
    val keySchemaOpt: Option[MetaData.KeySchema] = annotationForKeySchema(keySchema)
    val resultSchema                             = keySchemaOpt match {
      case Some(keySchemaAnnotation) => valueSchema.annotate(keySchemaAnnotation)
      case None                      => valueSchema
    }
    JsonSchema.Object(
      Map.empty,
      Right(resultSchema),
      Chunk.empty,
    )
  }

  private def jsonSchemaFromAnyMapSchema[K, V](
    keySchema: Schema[K],
    valueSchema: Schema[V],
    refType: SchemaStyle,
  ): JsonSchema.Object = {
    val valuesSchema = fromZSchema(valueSchema, refType)
    annotateMapSchemaWithKeysSchema(valuesSchema, keySchema)
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
              fromZSchema(
                field.annotations.foldLeft(field.schema)((schema, annotation) => schema.annotate(annotation)),
                SchemaStyle.Compact,
              )
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
          .description(descriptionFromAnnotations(record.annotations))
      case collection: Schema.Collection[_, _]                                                    =>
        collection match {
          case Schema.Sequence(elementSchema, _, _, _, _)                =>
            JsonSchema.ArrayType(Some(fromZSchema(elementSchema, refType)), None, uniqueItems = false)
          case Schema.NonEmptySequence(elementSchema, _, _, _, identity) =>
            JsonSchema.ArrayType(
              Some(fromZSchema(elementSchema, refType)),
              None,
              uniqueItems = identity == "NonEmptySet",
            )
          case Schema.Map(keySchema, valueSchema, _)                     =>
            jsonSchemaFromAnyMapSchema(keySchema, valueSchema, refType)
          case Schema.NonEmptyMap(keySchema, valueSchema, _)             =>
            jsonSchemaFromAnyMapSchema(keySchema, valueSchema, refType)
          case Schema.Set(elementSchema, _)                              =>
            JsonSchema.ArrayType(Some(fromZSchema(elementSchema, refType)), None, uniqueItems = true)
        }
      case Schema.Transform(schema, _, _, _, _)                                                   =>
        fromZSchema(schema, refType)
      case Schema.Primitive(standardType, annotations)                                            =>
        standardType match {
          case StandardType.UnitType           => JsonSchema.Null
          case StandardType.StringType         =>
            JsonSchema.String.fromValidation(
              annotations.collect { case zio.schema.annotation.validate(v) => v }.headOption,
            )
          case StandardType.BoolType           => JsonSchema.Boolean
          case StandardType.ByteType           => JsonSchema.String()
          case StandardType.ShortType          =>
            JsonSchema.Integer.fromValidation(
              IntegerFormat.Int32,
              annotations.collect { case zio.schema.annotation.validate(v) => v }.headOption,
            )
          case StandardType.IntType            =>
            JsonSchema.Integer.fromValidation(
              IntegerFormat.Int32,
              annotations.collect { case zio.schema.annotation.validate(v) => v }.headOption,
            )
          case StandardType.LongType           =>
            JsonSchema.Integer.fromValidation(
              IntegerFormat.Int64,
              annotations.collect { case zio.schema.annotation.validate(v) => v }.headOption,
            )
          case StandardType.FloatType          =>
            JsonSchema.Number.fromValidation(
              NumberFormat.Float,
              annotations.collect { case zio.schema.annotation.validate(v) => v }.headOption,
            )
          case StandardType.DoubleType         =>
            JsonSchema.Number.fromValidation(
              NumberFormat.Double,
              annotations.collect { case zio.schema.annotation.validate(v) => v }.headOption,
            )
          case StandardType.BinaryType         => JsonSchema.String()
          case StandardType.CharType           => JsonSchema.String()
          case StandardType.UUIDType           => JsonSchema.String(StringFormat.UUID)
          case StandardType.BigDecimalType     => // TODO: Is this correct?
            JsonSchema.Number.fromValidation(
              NumberFormat.Double,
              annotations.collect { case zio.schema.annotation.validate(v) => v }.headOption,
            )
          case StandardType.BigIntegerType     =>
            JsonSchema.Integer.fromValidation(
              IntegerFormat.Int64,
              annotations.collect { case zio.schema.annotation.validate(v) => v }.headOption,
            )
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
          case StandardType.CurrencyType       => JsonSchema.String()
        }

      case Schema.Optional(schema, _)    => fromZSchema(schema, refType).nullable(true)
      case Schema.Fail(_, _)             => throw new IllegalArgumentException("Fail schema is not supported.")
      case Schema.Tuple2(left, right, _) => AllOfSchema(Chunk(fromZSchema(left, refType), fromZSchema(right, refType)))
      case Schema.Either(left, right, _) => OneOfSchema(Chunk(fromZSchema(left, refType), fromZSchema(right, refType)))
      case Schema.Fallback(left, right, true, _) =>
        OneOfSchema(
          Chunk(
            AllOfSchema(Chunk(fromZSchema(left, refType), fromZSchema(right, refType))),
            fromZSchema(left, refType),
            fromZSchema(right, refType),
          ),
        )
      case Schema.Fallback(left, right, _, _)    =>
        OneOfSchema(Chunk(fromZSchema(left, refType), fromZSchema(right, refType)))
      case Schema.Lazy(schema0)                  => fromZSchema(schema0(), refType)
      case Schema.Dynamic(_)                     => AnyJson

    }

  private def descriptionFromAnnotations(annotations: Chunk[Any]) = {
    def sanitize(str: java.lang.String): java.lang.String =
      str.linesIterator
        .map(_.trim.stripPrefix("/**").stripPrefix("/*").stripSuffix("*/").stripPrefix("*").trim)
        .filterNot(l => l == "\n" || l == "")
        .mkString("\n")
    annotations.collectFirst {
      case description(value) if value.trim.startsWith("/*") => sanitize(value)
      case description(value)                                => value
    }
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
    val description0 = descriptionFromAnnotations(schema.annotations)
    val defaultValue = schema.annotations.collectFirst { case fieldDefaultValue(value) => value }.map { _ =>
      s"${if (description0.isDefined) "\n" else ""}If not set, this field defaults to the value of the default annotation."
    }
    Some(description0.getOrElse("") + defaultValue.getOrElse(""))
      .filter(_.nonEmpty)
  }

  private def fieldDefault(schema: Schema.Field[_, _]): Option[Json] =
    schema.annotations.collectFirst { case fieldDefaultValue(value) => value }
      .map(toJsonAst(schema.schema, _))

  @tailrec
  private def nominal(schema: Schema[_], referenceType: SchemaStyle = SchemaStyle.Reference): Option[java.lang.String] =
    schema match {
      case enumSchema: Schema.Enum[_]                 => refForTypeId(enumSchema.id, referenceType)
      case record: Schema.Record[_]                   => refForTypeId(record.id, referenceType)
      case lazySchema: Schema.Lazy[_]                 => nominal(lazySchema.schema, referenceType)
      case transformSchema: Schema.Transform[_, _, _] => nominal(transformSchema.schema, referenceType)
      case _                                          => None
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
        case _: MetaData.KeySchema                 =>
          // This is used only for additionalProperties schema,
          // where this annotation captures the schema for key values only.
          throw new IllegalStateException("KeySchema annotation should be stripped from schema prior to serialization")
      }
    }
  }

  sealed trait MetaData extends Product with Serializable
  object MetaData {
    final case class KeySchema(schema: JsonSchema)                         extends MetaData
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
        notNullableSchemas =
          obj.withoutAnnotations
            .asInstanceOf[JsonSchema.Object]
            .properties
            .filterNot { case (_, schema) => schema.isNullable }
        if notNullableSchemas == otherObj.withoutAnnotations.asInstanceOf[JsonSchema.Object].properties
      } yield otherObj).distinct

      val minified = objects.filterNot(markedForRemoval.contains).map { obj =>
        val annotations        = obj.annotations
        val asObject           = obj.withoutAnnotations.asInstanceOf[JsonSchema.Object]
        val notNullableSchemas = asObject.properties.filterNot { case (_, schema) => schema.isNullable }
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

  final case class Number(
    format: NumberFormat,
    minimum: Option[Double] = None,
    exclusiveMinimum: Option[Either[Boolean, Double]] = None,
    maximum: Option[Double] = None,
    exclusiveMaximum: Option[Either[Boolean, Double]] = None,
    multipleOf: Option[Double] = None,
  ) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("number")),
        format = Some(format.productPrefix.toLowerCase),
        minimum = minimum.map(Left(_)),
        exclusiveMinimum = exclusiveMinimum.map(_.map(Left(_))),
        maximum = maximum.map(Left(_)),
        exclusiveMaximum = exclusiveMaximum.map(_.map(Left(_))),
      )
  }

  object Number {
    def fromValidation(format: NumberFormat, validation: Option[Validation[_]]): JsonSchema = {
      validation match {
        case None        => Number(format, None, None, None, None, None)
        case Some(value) =>
          val flattened = flatten(value.bool.asInstanceOf[Bool[Predicate[_]]])

          val exclusiveMin = flattened.collectFirst { case Predicate.Num.GreaterThan(num, v) =>
            Right(num.numeric.toDouble(v))
          }

          val exclusiveMax = flattened.collectFirst { case Predicate.Num.LessThan(num, v) =>
            Right(num.numeric.toDouble(v))
          }

          val min        = None
          val max        = None
          val multipleOf = None

          Number(format, min, exclusiveMin, max, exclusiveMax, multipleOf)

      }

    }

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

  final case class Integer(
    format: IntegerFormat,
    minimum: Option[Long] = None,
    exclusiveMinimum: Option[Either[Boolean, Long]] = None,
    maximum: Option[Long] = None,
    exclusiveMaximum: Option[Either[Boolean, Long]] = None,
    multipleOf: Option[Long] = None,
  ) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("integer")),
        format = Some(format.productPrefix.toLowerCase),
        minimum = minimum.map(Right(_)),
        exclusiveMinimum = exclusiveMinimum.map(_.map(Right(_))),
        maximum = maximum.map(Right(_)),
        exclusiveMaximum = exclusiveMaximum.map(_.map(Right(_))),
      )
  }

  def flatten(value: Bool[Predicate[_]], not: Boolean = false): Chunk[Predicate[_]] = {
    value match {
      case Bool.And(left, right)                                                                                =>
        flatten(left.asInstanceOf[Bool[Predicate[_]]], not) ++ flatten(right.asInstanceOf[Bool[Predicate[_]]], not)
      // Or is not possible to model in json schema
      case Bool.Or(_, _)                                                                                        =>
        Chunk.empty
      case Bool.Leaf(Predicate.Contramap(p, _))                                                                 =>
        flatten(p.asInstanceOf[Bool[Predicate[_]]], not)
      case Bool.Leaf(Predicate.Str.MaxLength(l)) if not                                                         =>
        Chunk(Predicate.Str.MinLength(l).asInstanceOf[Predicate[_]])
      case Bool.Leaf(Predicate.Str.MaxLength(l))                                                                =>
        Chunk(Predicate.Str.MaxLength(l).asInstanceOf[Predicate[_]])
      case Bool.Leaf(Predicate.Str.MinLength(l)) if not                                                         =>
        Chunk(Predicate.Str.MaxLength(l).asInstanceOf[Predicate[_]])
      case Bool.Leaf(Predicate.Str.MinLength(l))                                                                =>
        Chunk(Predicate.Str.MinLength(l).asInstanceOf[Predicate[_]])
      case Bool.Leaf(Predicate.Str.Matches(r)) if not                                                           =>
        Chunk(Predicate.Str.Matches(r).asInstanceOf[Predicate[_]])
      case Bool.Leaf(Predicate.Num.GreaterThan(num, v)) if not && num.isInstanceOf[NumType.IntType.type]        =>
        Chunk(
          Predicate.Num
            .LessThan(num.asInstanceOf[NumType[Any]], num.asInstanceOf[NumType[Any]].numeric.plus(v, 1))
            .asInstanceOf[Predicate[_]],
        )
      case Bool.Leaf(Predicate.Num.GreaterThan(num, v)) if not && num.isInstanceOf[NumType.LongType.type]       =>
        Chunk(
          Predicate.Num
            .LessThan(num.asInstanceOf[NumType[Any]], num.asInstanceOf[NumType[Any]].numeric.plus(v, 1))
            .asInstanceOf[Predicate[_]],
        )
      case Bool.Leaf(Predicate.Num.GreaterThan(num, v)) if not && num.isInstanceOf[NumType.ShortType.type]      =>
        Chunk(
          Predicate.Num
            .LessThan(num.asInstanceOf[NumType[Any]], num.asInstanceOf[NumType[Any]].numeric.plus(v, 1))
            .asInstanceOf[Predicate[_]],
        )
      case Bool.Leaf(Predicate.Num.GreaterThan(num, v)) if not && num.isInstanceOf[NumType.BigIntType.type]     =>
        Chunk(
          Predicate.Num
            .LessThan(num.asInstanceOf[NumType[Any]], num.asInstanceOf[NumType[Any]].numeric.plus(v, 1))
            .asInstanceOf[Predicate[_]],
        )
      case Bool.Leaf(Predicate.Num.GreaterThan(num, _)) if not && num.isInstanceOf[NumType.FloatType.type]      =>
        throw new IllegalArgumentException("Inverted Float predicated can't be compiled to json schema")
      case Bool.Leaf(Predicate.Num.GreaterThan(num, _)) if not && num.isInstanceOf[NumType.DoubleType.type]     =>
        throw new IllegalArgumentException("Inverted Double predicated can't be compiled to json schema")
      case Bool.Leaf(Predicate.Num.GreaterThan(num, _)) if not && num.isInstanceOf[NumType.BigDecimalType.type] =>
        throw new IllegalArgumentException("Inverted BigDecimal predicated can't be compiled to json schema")
      case Bool.Leaf(p @ Predicate.Num.GreaterThan(_, _))                                                       =>
        Chunk(p.asInstanceOf[Predicate[_]])
      case Bool.Leaf(Predicate.Num.LessThan(num, v)) if not && num.isInstanceOf[NumType.IntType.type]           =>
        Chunk(
          Predicate.Num
            .GreaterThan(num.asInstanceOf[NumType[Any]], num.asInstanceOf[NumType[Any]].numeric.minus(v, 1))
            .asInstanceOf[Predicate[_]],
        )
      case Bool.Leaf(Predicate.Num.LessThan(num, v)) if not && num.isInstanceOf[NumType.LongType.type]          =>
        Chunk(
          Predicate.Num
            .GreaterThan(num.asInstanceOf[NumType[Any]], num.asInstanceOf[NumType[Any]].numeric.minus(v, 1))
            .asInstanceOf[Predicate[_]],
        )
      case Bool.Leaf(Predicate.Num.LessThan(num, v)) if not && num.isInstanceOf[NumType.ShortType.type]         =>
        Chunk(
          Predicate.Num
            .GreaterThan(num.asInstanceOf[NumType[Any]], num.asInstanceOf[NumType[Any]].numeric.minus(v, 1))
            .asInstanceOf[Predicate[_]],
        )
      case Bool.Leaf(Predicate.Num.LessThan(num, v)) if not && num.isInstanceOf[NumType.BigIntType.type]        =>
        Chunk(
          Predicate.Num
            .LessThan(num.asInstanceOf[NumType[Any]], num.asInstanceOf[NumType[Any]].numeric.minus(v, 1))
            .asInstanceOf[Predicate[_]],
        )
      case Bool.Leaf(Predicate.Num.LessThan(num, _)) if not && num.isInstanceOf[NumType.FloatType.type]         =>
        throw new IllegalArgumentException("Inverted Float predicated can't be compiled to json schema")
      case Bool.Leaf(Predicate.Num.LessThan(num, _)) if not && num.isInstanceOf[NumType.DoubleType.type]        =>
        throw new IllegalArgumentException("Inverted Double predicated can't be compiled to json schema")
      case Bool.Leaf(Predicate.Num.LessThan(num, _)) if not && num.isInstanceOf[NumType.BigDecimalType.type]    =>
        throw new IllegalArgumentException("Inverted BigDecimal predicated can't be compiled to json schema")
      case Bool.Leaf(p @ Predicate.Num.LessThan(_, _))                                                          =>
        Chunk(p.asInstanceOf[Predicate[_]])
      case Bool.Not(value)                                                                                      =>
        flatten(value, !not)
      case _                                                                                                    =>
        Chunk.empty
    }
  }

  object Integer {

    def fromValidation(format: IntegerFormat, validation: Option[Validation[_]]): JsonSchema = {
      validation match {
        case None        => Integer(format, None, None, None, None, None)
        case Some(value) =>
          val flattened = flatten(value.bool.asInstanceOf[Bool[Predicate[_]]])

          val exclusiveMin = flattened.collectFirst { case Predicate.Num.GreaterThan(num, v) =>
            Right(num.numeric.toLong(v))
          }

          val exclusiveMax = flattened.collectFirst { case Predicate.Num.LessThan(num, v) =>
            Right(num.numeric.toLong(v))
          }

          val min        = None
          val max        = None
          val multipleOf = None

          Integer(format, min, exclusiveMin, max, exclusiveMax, multipleOf)

      }

    }
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
  final case class String(
    format: Option[StringFormat],
    pattern: Option[Pattern],
    maxLength: Option[Int] = None,
    minLength: Option[Int] = None,
  ) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("string")),
        format = format.map(_.value),
        pattern = pattern.map(_.value),
        maxLength = maxLength,
        minLength = minLength,
      )
  }

  object String {
    def apply(format: StringFormat): String = String(Some(format), None)
    def apply(pattern: Pattern): String     = String(None, Some(pattern))
    def apply(): String                     = String(None, None)

    def fromValidation(validation: Option[Validation[_]]): JsonSchema =
      if (validation.isEmpty) {
        String(None, None)
      } else {
        val flattened = flatten(validation.get.bool.asInstanceOf[Bool[Predicate[_]]])
        val pattern   =
          flattened.collectFirst { case Predicate.Str.Matches(r) => Regex.toRegexString(r) }
        val maxLength = flattened.collectFirst { case Predicate.Str.MaxLength(l) => l }
        val minLength = flattened.collectFirst { case Predicate.Str.MinLength(l) => l }
        String(None, pattern.map(Pattern.apply), maxLength, minLength)
      }
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

  final case class ArrayType(items: Option[JsonSchema], minItems: Option[Int], uniqueItems: Boolean)
      extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("array")),
        items = items.map(_.toSerializableSchema),
        minItems = minItems,
        uniqueItems = if (uniqueItems) Some(true) else None,
      )
  }

  final case class Object(
    properties: Map[java.lang.String, JsonSchema],
    additionalProperties: Either[Boolean, JsonSchema],
    required: Chunk[java.lang.String],
  ) extends JsonSchema {

    /**
     * This Object represents an "open dictionary", aka a Map
     *
     * See: https://github.com/zio/zio-http/issues/3048#issuecomment-2306291192
     */
    def isOpenDictionary: Boolean = properties.isEmpty && additionalProperties.isRight

    /**
     * This Object represents a "closed dictionary", aka a case class
     *
     * See: https://github.com/zio/zio-http/issues/3048#issuecomment-2306291192
     */
    def isClosedDictionary: Boolean = additionalProperties.isLeft

    /**
     * Can't represent a case class and a Map at the same time
     */
    def isInvalid: Boolean = properties.nonEmpty && additionalProperties.isRight

    def addAll(value: Chunk[(java.lang.String, JsonSchema)]): Object =
      value.foldLeft(this) { case (obj, (name, schema)) =>
        schema match {
          case thatObj @ Object(properties, additionalProperties, required)
              if thatObj.isClosedDictionary && additionalProperties == Left(false) &&
                !(this.properties == properties && this.additionalProperties == additionalProperties) =>
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

    private def reconcileIfBothDefined[T](left: Option[T], right: Option[T])(combine: (T, T) => Option[T]): Option[T] =
      for {
        l <- left
        r <- right
        c <- combine(l, r)
      } yield c

    private def reconcileOrEither[T](left: Option[T], right: Option[T])(combine: (T, T) => Option[T]): Option[T] =
      (left, right) match {
        case (Some(l), Some(r)) => combine(l, r)
        case (lOption, rOption) => lOption.orElse(rOption)
      }

    private def someWhenEq[T](l: T, r: T): Option[T] =
      if (l == r) Some(l) else None

    private def combinePatterns(lPattern: Pattern, rPattern: Pattern): Some[Pattern] =
      if (lPattern == rPattern) Some(lPattern)
      else {
        // validate either pattern match.
        //
        // If we to enforce AND semantics rather than OR semantics,
        // we can be easily achieve this with lookahead assertions: {{{
        //   Pattern("(?=" + lPattern + ")(?=" + rPattern + ")")
        // }}}
        Some(Pattern("(" + lPattern + ")|(" + rPattern + ")"))
      }

    private def wrap[T](f: (T, T) => T): (T, T) => Option[T] = (l, r) => Some(f(l, r))

    /**
     * When combining additionalProperties (AKA dictionaries) schemas, usually
     * we should deal only with the values schemas.
     *
     * Since a support for "x-string-key-schema" extension was added, we also
     * have schemas for the dictionary keys. This is not supported natively in
     * OpenAPI, which allows only string keys without schema.
     *
     * By allowing this extension, we are still restricted to only use string
     * keys so we adhere to OpenAPI's rules, but we can have a more fine-grained
     * semantics.
     *
     * For instance, having keys as UUID instead of plain strings, referencing
     * aliased Newtype strings, or have other OpenAPI string validations like
     * pattern, or length.
     *
     * In here, we attempt to reconcile 2 key schemas. They must be Strings, (or
     * else we'll throw). And we attempt to make them match the best we can:
     *   - only keep format if match on both
     *   - adjust the pattern to enforce both patterns if both exist
     *   - max length is restricted to be the minimum
     *   - min length is restricted to be the maximum
     *   - if after reconciliation min > max, we discard both requirements
     *
     * @param left
     * @param right
     * @return
     */
    private def combineKeySchemasForAdditionalProperties(
      left: Option[JsonSchema],
      right: Option[JsonSchema],
    ): Option[JsonSchema] = {

      // TODO: what happens in case of annotated key schemas?
      //       Is it possible?
      //       How should we combine if so?
      //       Meanwhile, we just discard an key schema annotations.
      //       key schemas are a special extension without support in other libraries, so it's fine.
      val lKeyNoAnnotations = left.map(_.withoutAnnotations)
      val rKeyNoAnnotations = right.map(_.withoutAnnotations)

      reconcileIfBothDefined(lKeyNoAnnotations, rKeyNoAnnotations) {
        case (JsonSchema.String(lfmt, lptn, lmxl, lmnl), JsonSchema.String(rfmt, rptn, rmxl, rmnl)) =>
          Some(
            JsonSchema.String(
              format = reconcileIfBothDefined(lfmt, rfmt)(someWhenEq),
              pattern = reconcileIfBothDefined(lptn, rptn)(combinePatterns),
              maxLength = reconcileOrEither(lmxl, rmxl)(wrap(math.max)),
              minLength = reconcileOrEither(lmnl, rmnl)(wrap(math.min)),
            ),
          )
        case (l, r) => throw new IllegalArgumentException(s"dictionary keys must be of string schemas! got: $l, $r")
      }
    }

    private def combineAdditionalProperties(
      left: Either[Boolean, JsonSchema],
      right: Either[Boolean, JsonSchema],
    ): Either[Boolean, JsonSchema] =
      (left, right) match {
        case (Left(false), _)                 => Left(false)
        case (_, Left(_))                     => left
        case (Left(true), _)                  => right
        case (Right(lSchema), Right(rSchema)) =>
          val (leftKey, lAnnotations)  = Object.extractKeySchemaFromAnnotations(lSchema)
          val (rightKey, rAnnotations) = Object.extractKeySchemaFromAnnotations(rSchema)

          val keySchema = combineKeySchemasForAdditionalProperties(leftKey, rightKey)

          val leftVal  = lSchema.withoutAnnotations.annotate(lAnnotations)
          val rightVal = rSchema.withoutAnnotations.annotate(rAnnotations)

          // TODO: should we flatten AllOfSchemas here?
          val combined          = AllOfSchema(Chunk(leftVal, rightVal))
          val annotatedCombined = keySchema.fold[JsonSchema](combined)(ks => combined.annotate(MetaData.KeySchema(ks)))

          Right(annotatedCombined)
      }

    override protected[openapi] def toSerializableSchema: SerializableJsonSchema = {
      val additionalProperties = this.additionalProperties match {
        case Left(true)  => None
        case Left(false) => Some(BoolOrSchema.BooleanWrapper(false))
        case Right(js)   =>
          val (keySchemaOpt, nonKeySchemaAnnotations) = Object.extractKeySchemaFromAnnotations(js)
          val filteredKeySchemaOpt                    = keySchemaOpt.filterNot {
            case JsonSchema.String(None, None, None, None) => true // no need to annotate a plain string key
            case _                                         => false
          }
          val valueSerializedSchema                   =
            js.withoutAnnotations
              .annotate(nonKeySchemaAnnotations)
              .toSerializableSchema
              .copy(optionalKeySchema = filteredKeySchemaOpt.map(_.toSerializableSchema))

          Some(BoolOrSchema.SchemaWrapper(valueSerializedSchema))
      }

      val nullableFields = properties.collect { case (name, schema) if schema.isNullable => name }.toSet

      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("object")),
        properties = Some(properties.map { case (name, schema) => name -> schema.toSerializableSchema }),
        additionalProperties = additionalProperties,
        required =
          if (required.isEmpty) None
          else if (nullableFields.isEmpty) Some(required)
          else {
            val newRequired = required.filterNot(nullableFields.contains)
            if (newRequired.isEmpty) None else Some(newRequired)
          },
      )
    }
  }

  object Object {
    val empty: JsonSchema.Object = JsonSchema.Object(Map.empty, Left(true), Chunk.empty)

    private[http] def extractKeySchemaFromAnnotations(js: JsonSchema): (Option[JsonSchema], Chunk[MetaData]) =
      js.annotations.foldLeft(Option.empty[JsonSchema] -> Chunk.empty[MetaData]) {
        case ((kSchemaOpt, otherAnnotations), MetaData.KeySchema(s)) => kSchemaOpt.orElse(Some(s)) -> otherAnnotations
        case ((kSchemaOpt, otherAnnotations), noKeySchemaAnnotation) =>
          kSchemaOpt -> (otherAnnotations :+ noKeySchemaAnnotation)
      }
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

  /**
   * Errors that may arise while attempting to fully inline a schema using
   * `fromZSchemaInlineDeepOrFail`.
   */
  sealed trait GenerationError extends Product with Serializable

  object GenerationError {

    final case class RecursionDetected(cycle: NonEmptyChunk[java.lang.String]) extends GenerationError {
      override def toString: java.lang.String =
        s"Recursion detected: ${cycle.mkString(" -> ")}"
    }
  }

  /**
   * Builds a fully inlined `JsonSchema`, eliminating all `$ref`s. If a
   * recursive definition is encountered, the process short-circuits and returns
   * `Left(GenerationError.RecursionDetected)` containing the cycle (in
   * compact-reference form) that made inlining impossible.
   */
  def fromZSchemaInlineDeepOrFail[A](schema: Schema[A]): Either[GenerationError, JsonSchema] = {

    def token(ref: java.lang.String): java.lang.String = {
      val Prefix = "#/components/schemas/"
      val simple = if (ref.startsWith(Prefix)) ref.substring(Prefix.length) else ref
      simple.split("_").last
    }

    def compactVersion(ref: java.lang.String): java.lang.String = {
      val Prefix = "#/components/schemas/"
      if (ref.startsWith(Prefix)) Prefix + token(ref) else ref
    }

    // Build component dictionary using the reference style – this gives us an
    // easy mapping from the `$ref` strings produced by `fromZSchema` back to
    // their definitions.
    val multi = fromZSchemaMulti(schema, SchemaStyle.Reference)

    // Components map (includes root if it has a ref)
    val initialDict: Map[java.lang.String, JsonSchema] = multi.rootRef.map(r => r -> multi.root).toMap ++ multi.children

    // Also index components by their compact "#/.../{TypeName}" variant so that
    // a schema produced with `SchemaStyle.Compact` can be resolved as well.
    val dict: Map[java.lang.String, JsonSchema] = initialDict ++ initialDict.flatMap { case (ref, schema) =>
      val c = compactVersion(ref)
      if (c == ref) None else Some(c -> schema)
    }

    // Helper that recursively replaces RefSchema with their definitions while tracking refs.
    // NOTE: Method was previously called `inline` but Scala 3 treats `inline` as a soft keyword.
    // Renaming to avoid the conflict.
    def inlineRec(js: JsonSchema, path: List[java.lang.String]): Either[GenerationError, JsonSchema] =
      js match {
        case RefSchema(ref0) =>
          val tok     = token(ref0)
          val compact = compactVersion(ref0)
          if (path.contains(tok)) {
            val nec = NonEmptyChunk(compact)
            Left(GenerationError.RecursionDetected(nec))
          } else {
            dict.get(ref0).orElse(dict.get(compact)) match {
              case Some(target) => inlineRec(target, path :+ tok)
              case None         => Right(js)
            }
          }

        // Primitive / terminal types – nothing to inline.
        case primitive if primitive.isPrimitive || primitive == JsonSchema.Null || primitive == JsonSchema.AnyJson =>
          Right(primitive)

        // Composite types – recurse into children and rebuild if any changed.
        case JsonSchema.Object(props, addProps, req) =>
          val inlinedPropsEither =
            props.foldLeft[Either[GenerationError, Map[java.lang.String, JsonSchema]]](
              Right(Map.empty[java.lang.String, JsonSchema]),
            ) { case (accE, (k, v)) =>
              for {
                acc <- accE
                inV <- inlineRec(v, path)
              } yield acc + (k -> inV)
            }

          // additionalProperties may carry a schema, we need to dive into it.
          val inlinedAddPropsE: Either[GenerationError, Either[Boolean, JsonSchema]] = addProps match {
            case Right(apSchema) => inlineRec(apSchema, path).map(Right(_))
            case Left(b)         => Right(Left(b))
          }

          for {
            inProps <- inlinedPropsEither
            inAdd   <- inlinedAddPropsE
          } yield JsonSchema.Object(inProps, inAdd, req)

        case JsonSchema.ArrayType(items, minItems, unique) =>
          items match {
            case Some(it) => inlineRec(it, path).map(s => JsonSchema.ArrayType(Some(s), minItems, unique))
            case None     => Right(js)
          }

        case JsonSchema.OneOfSchema(schemas) =>
          recurseCollection(schemas, path).map(JsonSchema.OneOfSchema.apply)

        case JsonSchema.AllOfSchema(schemas) =>
          recurseCollection(schemas, path).map(JsonSchema.AllOfSchema.apply)

        case JsonSchema.AnyOfSchema(schemas) =>
          recurseCollection(schemas, path).map(JsonSchema.AnyOfSchema.apply)

        // Numbers, Strings, Integers already inline.
        case other => Right(other)
      }

    def recurseCollection(
      coll: Chunk[JsonSchema],
      path: List[java.lang.String],
    ): Either[GenerationError, Chunk[JsonSchema]] = {
      Chunk
        .fromIterable(coll)
        .foldLeft[Either[GenerationError, List[JsonSchema]]](Right(Nil)) { (accE, schema) =>
          for {
            acc <- accE
            inl <- inlineRec(schema, path)
          } yield inl :: acc
        }
        .map(lst => Chunk.fromIterable(lst.reverse))
    }

    inlineRec(multi.root, Nil)
  }

}
