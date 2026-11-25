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
  @fieldName("$schema") schema: Option[String] = None,
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
  @fieldName("$defs") defs: Option[Map[String, SerializableJsonSchema]] = None,
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
   *   // no-check: JSON output, not Scala code
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

  /** Adds the type name `value` to this type list, keeping each name once. */
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

/**
 * The result of `JsonSchema.fromZSchemaMultiple`: the `root` schema, the
 * reference name of the root type in `rootRef`, and in `children` the schemas
 * of the named records and enums that the root refers to, keyed by reference
 * name, such as `#/components/schemas/Address`. `rootRef` is `None` for a type
 * with no name, such as `Int`, and for the style `SchemaStyle.Inline`.
 */
final case class JsonSchemas(
  root: JsonSchema,
  rootRef: Option[String],
  children: Map[String, JsonSchema],
)

/**
 * A JSON Schema, as used for the request and response bodies of an OpenAPI
 * document. The cases are `String`, `Number`, `Integer`, `Boolean`, `Null`,
 * `ArrayType`, `Object` and `Enum`, the combinations `OneOfSchema`,
 * `AllOfSchema` and `AnyOfSchema`, `RefSchema` for a `\$ref`, `AnyJson` for any
 * value, and `AnnotatedSchema`, which adds one `MetaData` keyword such as a
 * description. Build one by hand or from a ZIO Schema with
 * `JsonSchema.fromZSchema`, and write it with `toJson`.
 *
 * {{{
 * import zio.schema.Schema
 *
 * val email: JsonSchema =
 *   JsonSchema.String(JsonSchema.StringFormat.Email).nullable(true).description("Contact address")
 *
 * email.toJson
 * // {"type":["string","null"],"format":"email","description":"Contact address"}
 *
 * val ports: JsonSchema =
 *   JsonSchema.fromZSchema(
 *     Schema[List[Int]],
 *     JsonSchema.SchemaRef(JsonSchema.SchemaSpec.OpenAPI, JsonSchema.SchemaStyle.Inline)
 *   )
 *
 * ports.toJson
 * // {"type":"array","items":{"type":"integer","format":"int32"}}
 * }}}
 */
sealed trait JsonSchema extends Product with Serializable { self =>

  /**
   * Encodes this schema as compact JSON bytes, in the same form as `toJson`.
   * Fields that are not set are left out.
   */
  def toJsonBytes: Chunk[Byte] = JsonCodec.schemaBasedBinaryCodec[JsonSchema].encode(self)

  /**
   * Returns this schema as a compact JSON string, such as `{"type":"string"}`
   * for `JsonSchema.String()`. Fields that are not set are left out.
   */
  def toJson: String = toJsonBytes.asString

  protected[openapi] def toSerializableSchema: SerializableJsonSchema

  /**
   * Wraps this schema in one `AnnotatedSchema` per entry of `annotations`, in
   * order, so the last entry is the outermost. An empty chunk returns this
   * schema unchanged.
   */
  def annotate(annotations: Chunk[JsonSchema.MetaData]): JsonSchema =
    annotations.foldLeft(self) { case (schema, annotation) => schema.annotate(annotation) }

  /** Wraps this schema in an `AnnotatedSchema` that adds `annotation`. */
  def annotate(annotation: JsonSchema.MetaData): JsonSchema =
    JsonSchema.AnnotatedSchema(self, annotation)

  /**
   * Returns the `MetaData` of the `AnnotatedSchema` layers around this schema,
   * innermost first, or an empty chunk when there is none.
   */
  def annotations: Chunk[JsonSchema.MetaData] = self match {
    case JsonSchema.AnnotatedSchema(schema, annotation) => schema.annotations :+ annotation
    case _                                              => Chunk.empty
  }

  /**
   * Returns `true` when one of this schema's annotations is
   * `MetaData.Nullable(true)`.
   */
  final def isNullable: Boolean =
    annotations.exists {
      case MetaData.Nullable(nullable) => nullable
      case _                           => false
    }

  /**
   * Returns this schema with its outer `AnnotatedSchema` layers removed.
   * Annotations on nested schemas, such as the properties of an `Object`, stay.
   */
  def withoutAnnotations: JsonSchema = self match {
    case JsonSchema.AnnotatedSchema(schema, _) => schema.withoutAnnotations
    case _                                     => self
  }

  /**
   * Adds `examples` as the `examples` keyword. An empty chunk writes
   * `"examples":[]`.
   */
  def examples(examples: Chunk[Json]): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Examples(examples))

  /**
   * Adds the value in `default` as the `default` keyword, or returns this
   * schema unchanged when it is `None`.
   */
  def default(default: Option[Json]): JsonSchema =
    default match {
      case Some(value) => JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Default(value))
      case None        => self
    }

  /** Adds `default` as the `default` keyword. */
  def default(default: Json): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Default(default))

  /**
   * Adds `description` as the `description` keyword. When it is added twice,
   * the last one is written.
   */
  def description(description: String): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Description(description))

  /**
   * Adds the text in `description` as the `description` keyword, or returns
   * this schema unchanged when it is `None`.
   */
  def description(description: Option[String]): JsonSchema =
    description match {
      case Some(value) => JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Description(value))
      case None        => self
    }

  /**
   * Returns the `description` keyword of this schema, or `None` when it has
   * none. A `RefSchema` or an `AllOfSchema` made nullable after its description
   * also returns `None`: the description then sits inside the written `anyOf`
   * (for a reference) or is not written at all (for `allOf`).
   */
  def description: Option[String] = self.toSerializableSchema.description

  /**
   * With `true`, marks this schema as accepting `null`; with `false`, returns
   * this schema unchanged. The JSON output adds `"null"` to the `type`
   * (`{"type":["string","null"]}`), appends `{"type":"null"}` to a `oneOf`,
   * `anyOf` or `allOf` list (an `allOf` then loses its other keywords), and
   * wraps a `RefSchema` in an `anyOf` with `{"type":"null"}`; the output of
   * `AnyJson` does not change.
   */
  def nullable(nullable: Boolean): JsonSchema =
    if (nullable) JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Nullable(true))
    else self

  /** Adds `discriminator` as the OpenAPI `discriminator` keyword. */
  def discriminator(discriminator: OpenAPI.Discriminator): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Discriminator(discriminator))

  /**
   * Adds the value in `discriminator` as the OpenAPI `discriminator` keyword,
   * or returns this schema unchanged when it is `None`.
   */
  def discriminator(discriminator: Option[OpenAPI.Discriminator]): JsonSchema =
    discriminator match {
      case Some(discriminator) =>
        JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Discriminator(discriminator))
      case None                =>
        self
    }

  /**
   * With `true`, adds `"deprecated":true`; with `false`, returns this schema
   * unchanged.
   */
  def deprecated(deprecated: Boolean): JsonSchema =
    if (deprecated) JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.Deprecated)
    else self

  /**
   * Adds the `contentEncoding` keyword. The value written is the case name in
   * lower case, such as `sevenbit` for `ContentEncoding.SevenBit` and `base64`
   * for `ContentEncoding.Base64`.
   */
  def contentEncoding(encoding: JsonSchema.ContentEncoding): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.ContentEncoding(encoding))

  /**
   * Adds `mediaType`, such as `image/png`, as the `contentMediaType` keyword.
   */
  def contentMediaType(mediaType: String): JsonSchema =
    JsonSchema.AnnotatedSchema(self, JsonSchema.MetaData.ContentMediaType(mediaType))

  /**
   * Returns `true` for a `String`, `Number`, `Integer`, `Boolean` or `Null`
   * schema. An `Enum`, `AnyJson` and any schema wrapped in an `AnnotatedSchema`
   * return `false`.
   */
  def isPrimitive: Boolean = self match {
    case _: JsonSchema.Number  => true
    case _: JsonSchema.Integer => true
    case _: JsonSchema.String  => true
    case JsonSchema.Boolean    => true
    case JsonSchema.Null       => true
    case _                     => false
  }

  /**
   * Returns `true` for an `ArrayType`, and for an `Object` with no properties
   * whose `additionalProperties` is a schema (a map). A schema wrapped in an
   * `AnnotatedSchema` returns `false`.
   */
  def isCollection: Boolean = self match {
    case _: JsonSchema.ArrayType => true
    case obj: JsonSchema.Object  => obj.properties.isEmpty && obj.additionalProperties.isRight
    case _                       => false
  }

}

/**
 * Constructors of [[JsonSchema]], its cases and formats, and the conversions
 * from a ZIO Schema, a text codec or a path segment codec.
 */
object JsonSchema {

  /**
   * The `Schema` that reads and writes a [[JsonSchema]] as JSON Schema
   * keywords. Decoding throws a `java.lang.IllegalArgumentException` (`Can't
   * convert ...`) instead of returning a `Left` for a `type` list without an
   * `enum`, such as the `["string","null"]` that `nullable(true)` writes on a
   * `String`, and for an unknown `type`, such as `"foo"`.
   */
  implicit val schema: Schema[JsonSchema] =
    SerializableJsonSchema.schema.transform[JsonSchema](JsonSchema.fromSerializableSchema, _.toSerializableSchema)

  private[openapi] val codec = JsonCodec.schemaBasedBinaryCodec[JsonSchema]

  private def toJsonAst(schema: Schema[_], v: Any): Json =
    JsonCodec
      .jsonEncoder(schema.asInstanceOf[Schema[Any]])
      .toJsonAST(v)
      .toOption
      .get

  /**
   * Returns the schema of a `TextCodec`: a one-value string `Enum` for a
   * constant, a `String` for text, an `Integer` in `int32` or `int64` for an
   * `Int` or a `Long`, `Boolean`, or a `String` in the `uuid` format for a
   * UUID.
   */
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

  /**
   * Returns the schema of a path segment codec: `Boolean`, an `Integer` in
   * `int32` or `int64`, a `String`, or a `String` in the `uuid` format. A
   * literal, empty, trailing or combined segment throws a
   * `java.lang.IllegalArgumentException`, such as
   * `Literal segment is not supported.`.
   */
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

  /**
   * Represents a complete JSON Schema document, consisting of:
   *
   * @param root
   *   The top-level [[JsonSchema]] for the document.
   * @param children
   *   A map of definition names to their corresponding [[JsonSchema]]
   *   instances.
   */
  case class JsonSchemaRoot(root: JsonSchema, children: Map[java.lang.String, JsonSchema]) {

    /**
     * Returns the serializable form of this document: the root schema with
     * `\$schema` set to `https://json-schema.org/draft/2020-12/schema` and the
     * children under `\$defs`.
     */
    def toSerializableSchema: SerializableJsonSchema = {
      root.toSerializableSchema.copy(
        schema = Some("https://json-schema.org/draft/2020-12/schema"),
        defs = Some(children.map { case (key, schema) => key -> schema.toSerializableSchema }),
      )
    }

    /**
     * Encodes this document as JSON. With `None` the JSON is compact; with
     * `Some(n)` it is pretty-printed with two spaces per level, starting at
     * nesting level `n`. Fields that are not set are left out.
     */
    def encodeJson(indent: Option[Int] = None): CharSequence =
      JsonCodec
        .jsonEncoder(
          JsonCodec.Configuration(
            explicitEmptyCollections = ExplicitConfig(encoding = false),
            explicitNulls = ExplicitConfig(encoding = false),
          ),
        )(JsonSchemaRoot.schema)
        .encodeJson(this, indent)

    /**
     * Serializes the schema into compact JSON (no pretty-printing).
     *
     * @return
     *   A JSON string containing the serialized schema.
     */
    def toJson: java.lang.String = encodeJson(None).toString

    /**
     * Serializes the schema into human-readable, pretty-printed JSON.
     *
     * @return
     *   A formatted JSON string representing the schema.
     */
    def toJsonPretty: java.lang.String = encodeJson(Some(0)).toString
  }

  /** Holds the `Schema` of [[JsonSchemaRoot]]. */
  object JsonSchemaRoot {

    /**
     * The `Schema` that reads and writes a [[JsonSchemaRoot]], with the
     * children under `\$defs`. Decoding throws a
     * `java.lang.IllegalArgumentException` for a `type` list without an `enum`,
     * such as the one that an optional field writes.
     */
    implicit val schema: Schema[JsonSchemaRoot] =
      SerializableJsonSchema.schema
        .transform[JsonSchemaRoot](JsonSchemaRoot.fromSerializableSchema, _.toSerializableSchema)

    private[openapi] def fromSerializableSchema(schema: SerializableJsonSchema): JsonSchemaRoot =
      JsonSchemaRoot(
        JsonSchema.fromSerializableSchema(schema),
        schema.defs.getOrElse(Map.empty).map { case (key, schema) => key -> JsonSchema.fromSerializableSchema(schema) },
      )
  }

  /**
   * Builds a JSON Schema document from the given ZIO `Schema`.
   *
   * @param schema
   *   The schema to convert into a JSON Schema document.
   * @return
   *   A [[JsonSchemaRoot]] whose root refers to named records and enums with
   *   `\$ref` links such as `#/\$defs/Address`, and whose children hold their
   *   schemas, keyed by type name.
   */
  def jsonSchema(schema: Schema[_]): JsonSchemaRoot = {
    val s = fromZSchemaMultiple(schema, SchemaRef(SchemaSpec.JsonSchema, SchemaStyle.Compact))
    JsonSchemaRoot(
      s.root,
      s.children.map { case (key, schema) => key.replace(SchemaSpec.JsonSchema.path, "") -> schema },
    )
  }

  /**
   * Same as `fromZSchemaMultiple` with `SchemaSpec.OpenAPI` and the style
   * `refType`.
   */
  @deprecated("use fromZSchemaMultiple", "3.8")
  def fromZSchemaMulti(
    schema: Schema[_],
    refType: SchemaStyle = SchemaStyle.Inline,
    seen: Set[java.lang.String] = Set.empty,
  ): JsonSchemas = fromZSchemaMultiple(schema, SchemaRef(SchemaSpec.OpenAPI, refType), seen)

  /**
   * Converts a ZIO `Schema` to a [[JsonSchemas]]: the root schema, the
   * reference name of the root type, and the schemas of the named records and
   * enums it refers to, keyed by reference name. A root type whose reference
   * name is in `seen` gives a `RefSchema` and no children. With
   * `SchemaStyle.Inline` there are no children and no root reference, although
   * nested records in the root are still written as `\$ref` links. A
   * `Schema.Fail` throws a `java.lang.IllegalArgumentException` (`Fail schema
   * is not supported.`).
   */
  def fromZSchemaMultiple(
    schema: Schema[_],
    refType: SchemaRef,
    seen: Set[java.lang.String] = Set.empty,
  ): JsonSchemas = {
    val ref = nominal(schema, refType)
    if (ref.exists(seen.contains)) {
      JsonSchemas(RefSchema(ref.get), ref, Map.empty)
    } else {
      val seenWithCurrent = seen ++ ref
      schema match {
        case enum0: Schema.Enum[_] if enum0.cases.forall(_.schema.isInstanceOf[CaseClass0[_]]) =>
          JsonSchemas(fromZSchemaInternal(enum0, refType.inline), ref, Map.empty)
        case enum0: Schema.Enum[_]                                                             =>
          val discriminatorName0   =
            enum0.annotations.collectFirst { case discriminatorName(name) => name }
          val hasNoDiscriminator   = enum0.annotations.exists(_.isInstanceOf[noDiscriminator])
          val nonTransientCases    = flattenEnumCases(enum0)
          val shouldAddDiscrimProp = discriminatorName0.isDefined && !hasNoDiscriminator

          JsonSchemas(
            fromZSchemaInternal(enum0, refType.inline),
            ref,
            nonTransientCases.flatMap { c =>
              val key    =
                nominal(c.schema, refType)
                  .orElse(nominal(c.schema, refType.inline))
              val nested = fromZSchemaMultiple(
                c.schema,
                refType,
                seenWithCurrent,
              )

              val augmentedRoot =
                if (shouldAddDiscrimProp) {
                  val caseName0 =
                    c.annotations.collectFirst { case caseName(name) => name }.getOrElse(c.id)
                  withDiscriminatorProperty(nested.root, discriminatorName0.get, caseName0)
                } else {
                  nested.root
                }

              nested.children ++ key.map(_ -> augmentedRoot)
            }.toMap,
          )
        case record: Schema.Record[_]                                                          =>
          val children = record.fields
            .filterNot(_.annotations.exists(_.isInstanceOf[transientField]))
            .flatMap { field =>
              val nested = fromZSchemaMultiple(
                field.annotations.foldLeft(field.schema)((schema, annotation) => schema.annotate(annotation)),
                refType,
                seenWithCurrent,
              )
              nested.rootRef.fold(ifEmpty = nested.children)(k => nested.children + (k -> nested.root))
            }
            .toMap
          JsonSchemas(fromZSchemaInternal(record, refType.inline), ref, children)
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
          fromZSchemaMultiple(schema, refType, seen)
        case Schema.Primitive(_, _)                                                            =>
          JsonSchemas(fromZSchemaInternal(schema, refType.inline), ref, Map.empty)
        case Schema.Optional(schema, _)                                                        =>
          fromZSchemaMultiple(schema, refType, seenWithCurrent)
        case Schema.Fail(_, _)                                                                 =>
          throw new IllegalArgumentException("Fail schema is not supported.")
        case Schema.Tuple2(left, right, _)                                                     =>
          val leftSchema  = fromZSchemaMultiple(left, refType, seenWithCurrent)
          val rightSchema = fromZSchemaMultiple(right, refType, seenWithCurrent)
          JsonSchemas(
            AllOfSchema(Chunk(leftSchema.root, rightSchema.root)),
            ref,
            leftSchema.children ++ rightSchema.children,
          )
        case Schema.Either(left, right, _)                                                     =>
          val leftSchema  = fromZSchemaMultiple(left, refType, seenWithCurrent)
          val rightSchema = fromZSchemaMultiple(right, refType, seenWithCurrent)
          JsonSchemas(
            OneOfSchema(Chunk(leftSchema.root, rightSchema.root)),
            ref,
            leftSchema.children ++ rightSchema.children,
          )
        case Schema.Fallback(left, right, fullDecode, _)                                       =>
          val leftSchema  = fromZSchemaMultiple(left, refType, seenWithCurrent)
          val rightSchema = fromZSchemaMultiple(right, refType, seenWithCurrent)
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
          fromZSchemaMultiple(schema0(), refType, seen)
        case Schema.Dynamic(_)                                                                 =>
          JsonSchemas(AnyJson, None, Map.empty)
      }
    }
  }

  private def mapSchema[K, V](
    refType: SchemaRef,
    ref: Option[java.lang.String],
    seenWithCurrent: Set[java.lang.String],
    keySchema: Schema[K],
    valueSchema: Schema[V],
  ) = {
    val nested          = fromZSchemaMultiple(valueSchema, refType, seenWithCurrent)
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
    refType: SchemaRef,
    ref: Option[java.lang.String],
    elementSchema: Schema[_],
    seen: Set[java.lang.String],
    minItems: Option[Int] = None,
    uniqueItems: Boolean = false,
  ): JsonSchemas = {
    val nested = fromZSchemaMultiple(elementSchema, refType, seen)
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
        fromZSchemaInternal(nonSimple, SchemaRef(SchemaSpec.OpenAPI, SchemaStyle.Inline)) match {
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
    refType: SchemaRef,
  ): JsonSchema.Object = {
    val valuesSchema = fromZSchemaInternal(valueSchema, refType)
    annotateMapSchemaWithKeysSchema(valuesSchema, keySchema)
  }

  /**
   * Same as `fromZSchema` with `SchemaSpec.OpenAPI` and the style `refType`.
   */
  @deprecated("use SchemaRef instead of SchemaStyle", "3.8")
  def fromZSchema(schema: Schema[_], refType: SchemaStyle = SchemaStyle.Inline): JsonSchema =
    fromZSchema(schema, SchemaRef(SchemaSpec.OpenAPI, refType))

  /**
   * Converts a ZIO `Schema` to a [[JsonSchema]]. With `SchemaStyle.Inline` the
   * schemas of nested records and enums are written in place; with `Compact` or
   * `Reference` a named record or enum, the root included, becomes a
   * `RefSchema`, such as `{"\$ref":"#/components/schemas/Customer"}`. With
   * `Compact` or `Reference` an `Option` becomes a nullable schema. With
   * `Inline` only an `Option` field of a record does; an `Option` at the root
   * or inside a collection is written as its element schema, so
   * `Schema[Option[Int]]` gives `{"type":"integer","format":"int32"}`. A
   * `Schema.Fail` throws a `java.lang.IllegalArgumentException` (`Fail schema
   * is not supported.`).
   */
  def fromZSchema(schema: Schema[_], refType: SchemaRef): JsonSchema =
    if (refType.style == SchemaStyle.Inline) {
      val result = fromZSchemaMultiple(schema, refType.compact)
      inlineRefs(result.root, result.children)
    } else {
      fromZSchemaInternal(schema, refType)
    }

  private def flattenEnumCases(enum0: Schema.Enum[_]): Chunk[Schema.Case[_, _]] = {
    val nonTransient = enum0.cases.filterNot(_.annotations.exists(_.isInstanceOf[transientCase]))
    Chunk.fromIterable(nonTransient).flatMap { c =>
      c.schema match {
        case nestedEnum: Schema.Enum[_] => flattenEnumCases(nestedEnum)
        case Schema.Lazy(schema0)       =>
          schema0() match {
            case nestedEnum: Schema.Enum[_] => flattenEnumCases(nestedEnum)
            case _                          => Chunk(c)
          }
        case _                          => Chunk(c)
      }
    }
  }

  private def fromZSchemaInternal(schema: Schema[_], refType: SchemaRef): JsonSchema =
    schema match {
      case enum0: Schema.Enum[_]
          if refType.style != SchemaStyle.Inline && nominal(enum0, refType.reference).isDefined =>
        JsonSchema.RefSchema(nominal(enum0, refType).get)
      case enum0: Schema.Enum[_] if enum0.cases.forall(_.schema.isInstanceOf[CaseClass0[_]]) =>
        JsonSchema.Enum(
          enum0.cases.map(c =>
            EnumValue.Str(c.annotations.collectFirst { case caseName(name) => name }.getOrElse(c.id)),
          ),
        )
      case enum0: Schema.Enum[_]                                                             =>
        val noDiscriminator    = enum0.annotations.exists(_.isInstanceOf[noDiscriminator])
        val discriminatorName0 =
          enum0.annotations.collectFirst { case discriminatorName(name) => name }
        val nonTransientCases  = flattenEnumCases(enum0)
        if (noDiscriminator) {
          JsonSchema
            .OneOfSchema(nonTransientCases.map(c => fromZSchemaInternal(c.schema, refType.compact)))
        } else if (discriminatorName0.isDefined) {
          JsonSchema
            .OneOfSchema(nonTransientCases.map(c => fromZSchemaInternal(c.schema, refType.compact)))
            .discriminator(
              OpenAPI.Discriminator(
                propertyName = discriminatorName0.get,
                mapping = nonTransientCases.map { c =>
                  val name = c.annotations.collectFirst { case caseName(name) => name }.getOrElse(c.id)
                  name -> nominal(c.schema, refType)
                    .orElse(nominal(c.schema, refType.compact))
                    .get
                }.toMap,
              ),
            )
        } else {
          JsonSchema
            .OneOfSchema(nonTransientCases.map { c =>
              val name = c.annotations.collectFirst { case caseName(name) => name }.getOrElse(c.id)
              Object(
                Map(name -> fromZSchemaInternal(c.schema, refType.compact)),
                Left(false),
                Chunk(name),
              )
            })
        }
      case record: Schema.Record[_]
          if refType.style != SchemaStyle.Inline && nominal(record, refType.reference).isDefined =>
        JsonSchema.RefSchema(nominal(record, refType).get)
      case record: Schema.Record[_]                                                          =>
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
              fromZSchemaInternal(
                field.annotations.foldLeft(field.schema)((schema, annotation) => schema.annotate(annotation)),
                refType.compact,
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
      case collection: Schema.Collection[_, _]                                               =>
        collection match {
          case Schema.Sequence(elementSchema, _, _, _, _)                =>
            JsonSchema.ArrayType(Some(fromZSchemaInternal(elementSchema, refType)), None, uniqueItems = false)
          case Schema.NonEmptySequence(elementSchema, _, _, _, identity) =>
            JsonSchema.ArrayType(
              Some(fromZSchemaInternal(elementSchema, refType)),
              Some(1),
              uniqueItems = identity == "NonEmptySet",
            )
          case Schema.Map(keySchema, valueSchema, _)                     =>
            jsonSchemaFromAnyMapSchema(keySchema, valueSchema, refType)
          case Schema.NonEmptyMap(keySchema, valueSchema, _)             =>
            jsonSchemaFromAnyMapSchema(keySchema, valueSchema, refType)
          case Schema.Set(elementSchema, _)                              =>
            JsonSchema.ArrayType(Some(fromZSchemaInternal(elementSchema, refType)), None, uniqueItems = true)
        }
      case Schema.Transform(schema, _, _, _, _)                                              =>
        fromZSchemaInternal(schema, refType)
      case Schema.Primitive(standardType, annotations)                                       =>
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
          case StandardType.InstantType        => JsonSchema.String(StringFormat.DateTime)
          case StandardType.LocalDateType      => JsonSchema.String(StringFormat.Date)
          case StandardType.LocalTimeType      => JsonSchema.String(StringFormat.Time)
          case StandardType.LocalDateTimeType  => JsonSchema.String(StringFormat.DateTime)
          case StandardType.OffsetTimeType     => JsonSchema.String(StringFormat.Time)
          case StandardType.OffsetDateTimeType => JsonSchema.String(StringFormat.DateTime)
          case StandardType.ZonedDateTimeType  => JsonSchema.String(StringFormat.DateTime)
          case StandardType.CurrencyType       => JsonSchema.String()
        }

      case Schema.Optional(schema, _)            => fromZSchemaInternal(schema, refType).nullable(true)
      case Schema.Fail(_, _)                     => throw new IllegalArgumentException("Fail schema is not supported.")
      case Schema.Tuple2(left, right, _)         =>
        AllOfSchema(Chunk(fromZSchemaInternal(left, refType), fromZSchemaInternal(right, refType)))
      case Schema.Either(left, right, _)         =>
        OneOfSchema(Chunk(fromZSchemaInternal(left, refType), fromZSchemaInternal(right, refType)))
      case Schema.Fallback(left, right, true, _) =>
        OneOfSchema(
          Chunk(
            AllOfSchema(Chunk(fromZSchemaInternal(left, refType), fromZSchemaInternal(right, refType))),
            fromZSchemaInternal(left, refType),
            fromZSchemaInternal(right, refType),
          ),
        )
      case Schema.Fallback(left, right, _, _)    =>
        OneOfSchema(Chunk(fromZSchemaInternal(left, refType), fromZSchemaInternal(right, refType)))
      case Schema.Lazy(schema0)                  => fromZSchemaInternal(schema0(), refType)
      case Schema.Dynamic(_)                     => AnyJson

    }

  private def inlineRefs(schema: JsonSchema, defs: Map[java.lang.String, JsonSchema]): JsonSchema =
    schema match {
      case RefSchema(ref)                                     =>
        defs.get(ref).map(inlineRefs(_, defs)).getOrElse(schema)
      case Object(properties, additionalProperties, required) =>
        Object(
          properties.map { case (k, v) => k -> inlineRefs(v, defs) },
          additionalProperties.map(inlineRefs(_, defs)),
          required,
        )
      case ArrayType(items, minItems, uniqueItems)            =>
        ArrayType(items.map(inlineRefs(_, defs)), minItems, uniqueItems)
      case OneOfSchema(schemas)                               =>
        OneOfSchema(schemas.map(inlineRefs(_, defs)))
      case AllOfSchema(schemas)                               =>
        AllOfSchema(schemas.map(inlineRefs(_, defs)))
      case AnyOfSchema(schemas)                               =>
        AnyOfSchema(schemas.map(inlineRefs(_, defs)))
      case AnnotatedSchema(inner, annotation)                 =>
        AnnotatedSchema(inlineRefs(inner, defs), annotation)
      case other                                              => other
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

  /**
   * How `fromZSchema` and `fromZSchemaMultiple` name records and enums: no
   * reference name (`Inline`), a name built from the full type path
   * (`Reference`), or one built from the type name (`Compact`). With `Inline`,
   * `fromZSchema` writes them in place.
   */
  sealed trait SchemaStyle extends Product with Serializable

  /** The `SchemaStyle` cases. */
  object SchemaStyle {

    /**
     * Generates inline json schema: `fromZSchema` writes the schemas of nested
     * records and enums in place instead of as `\$ref` links.
     */
    case object Inline extends SchemaStyle

    /**
     * Generates unique schema names using the full package path
     * @see
     *   SchemaStyle.Compact for compact schema names.
     */
    case object Reference extends SchemaStyle

    /**
     * Generates schema names using the type name
     * @see
     *   SchemaStyle.Reference for full package path schema names to avoid name
     *   collisions.
     */
    case object Compact extends SchemaStyle
  }

  /**
   * The kind of document that schema references point into, which sets their
   * prefix: `#/components/schemas/` for `OpenAPI` and `#/\$defs/` for
   * `JsonSchema`.
   */
  sealed trait SchemaSpec extends Product with Serializable { private[openapi] def path: java.lang.String }

  /** The `SchemaSpec` cases. */
  object SchemaSpec {

    /**
     * Generates schema references for an Open API document, under
     * `#/components/schemas/`.
     */
    case object OpenAPI extends SchemaSpec { override def path: java.lang.String = "#/components/schemas/" }

    /**
     * Generates schema references for a JSON Schema document, under
     * `#/\$defs/`.
     */
    case object JsonSchema extends SchemaSpec { override def path: java.lang.String = "#/$defs/" }
  }

  /**
   * The target document and the style that `fromZSchema` and
   * `fromZSchemaMultiple` use for named records and enums.
   */
  final case class SchemaRef(spec: SchemaSpec, style: SchemaStyle) {

    /** Returns a copy with the style `SchemaStyle.Inline`. */
    def inline: SchemaRef = copy(style = SchemaStyle.Inline)

    /** Returns a copy with the style `SchemaStyle.Reference`. */
    def reference: SchemaRef = copy(style = SchemaStyle.Reference)

    /** Returns a copy with the style `SchemaStyle.Compact`. */
    def compact: SchemaRef = copy(style = SchemaStyle.Compact)
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
  private def nominal(
    schema: Schema[_],
    referenceType: SchemaRef,
  ): Option[java.lang.String] =
    schema match {
      case enumSchema: Schema.Enum[_]                 => refForTypeId(enumSchema.id, referenceType)
      case record: Schema.Record[_]                   => refForTypeId(record.id, referenceType)
      case lazySchema: Schema.Lazy[_]                 => nominal(lazySchema.schema, referenceType)
      case transformSchema: Schema.Transform[_, _, _] => nominal(transformSchema.schema, referenceType)
      case _                                          => None
    }

  private def refForTypeId(id: TypeId, referenceType: SchemaRef): Option[java.lang.String] =
    id match {
      case nominal: TypeId.Nominal if referenceType.style == SchemaStyle.Reference =>
        Some(s"${referenceType.spec.path}${nominal.fullyQualified.replace(".", "_")}")
      case nominal: TypeId.Nominal if referenceType.style == SchemaStyle.Compact   =>
        Some(s"${referenceType.spec.path}${nominal.typeName}")
      case _                                                                       =>
        None
    }

  /**
   * Adds a discriminator property to a JsonSchema. This is used when generating
   * OpenAPI and JSON Schema documents for sealed traits with a
   * `discriminatorName` annotation and no `noDiscriminator` annotation. Each
   * case class schema needs to include the discriminator property as a required
   * field with an enum value. A schema that is not an object is wrapped in an
   * `allOf` with an object that holds the property.
   *
   * @param schema
   *   The original case schema
   * @param discriminatorPropertyName
   *   The name of the discriminator property (from `discriminatorName`)
   * @param caseNameValue
   *   The value of the discriminator for this case (from `caseName`, or the
   *   case id)
   * @return
   *   A new JsonSchema with the discriminator property added
   */
  private def withDiscriminatorProperty(
    schema: JsonSchema,
    discriminatorPropertyName: java.lang.String,
    caseNameValue: java.lang.String,
  ): JsonSchema = {
    val discriminatorField = JsonSchema.Enum(Chunk(EnumValue.Str(caseNameValue)))

    schema.withoutAnnotations match {
      case obj: Object =>
        val annotations = schema.annotations
        Object(
          properties = obj.properties + (discriminatorPropertyName -> discriminatorField),
          additionalProperties = obj.additionalProperties,
          required = (Chunk(discriminatorPropertyName) ++ obj.required).distinct,
        ).annotate(annotations)
      case other       =>
        // For non-object schemas (like refs), wrap with allOf to add the discriminator property
        AllOfSchema(
          Chunk(
            other,
            Object(
              Map(discriminatorPropertyName -> discriminatorField),
              Left(false),
              Chunk(discriminatorPropertyName),
            ),
          ),
        ).annotate(schema.annotations)
    }
  }

  /**
   * Returns an `Object` schema with the given properties, all of them required,
   * and `additionalProperties` set to `false`. For a repeated name the last
   * schema is kept, and with more than four properties the written order can
   * differ from the argument order.
   */
  def obj(properties: (java.lang.String, JsonSchema)*): JsonSchema =
    JsonSchema.Object(
      properties = properties.toMap,
      additionalProperties = Left(false),
      required = Chunk.fromIterable(properties.toMap.keys),
    )

  /**
   * A schema with one `MetaData` keyword added, such as a description.
   * `annotate` builds it, as do helpers such as `description` and `nullable`.
   */
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

  /** A keyword that an `AnnotatedSchema` adds to its schema. */
  sealed trait MetaData extends Product with Serializable

  /** The `MetaData` cases. */
  object MetaData {

    /**
     * The schema of the keys of a map, written as the `x-string-key-schema`
     * keyword of the `additionalProperties` schema of an `Object`. Writing a
     * schema that carries a `KeySchema` anywhere else throws a
     * `java.lang.IllegalStateException`.
     */
    final case class KeySchema(schema: JsonSchema) extends MetaData

    /** The `examples` keyword. */
    final case class Examples(chunk: Chunk[Json]) extends MetaData

    /** The `default` keyword. */
    final case class Default(default: Json) extends MetaData

    /** The OpenAPI `discriminator` keyword. */
    final case class Discriminator(discriminator: OpenAPI.Discriminator) extends MetaData

    /**
     * Whether the schema accepts `null`. `true` writes the schema as
     * `nullable(true)` does, and `false` changes nothing in the output.
     */
    final case class Nullable(nullable: Boolean) extends MetaData

    /** The `description` keyword. */
    final case class Description(description: java.lang.String) extends MetaData

    /** The `contentEncoding` keyword. */
    final case class ContentEncoding(encoding: JsonSchema.ContentEncoding) extends MetaData

    /** The `contentMediaType` keyword. */
    final case class ContentMediaType(mediaType: java.lang.String) extends MetaData

    /** Writes `"deprecated":true`. */
    case object Deprecated extends MetaData
  }

  /**
   * A `contentEncoding` value. The JSON output holds the case name in lower
   * case (`sevenbit`, `eightbit`, `binary`, `quotedprintable`, `base16`,
   * `base32`, `base64`), while `fromString` and decoding read `7bit`, `8bit`,
   * `binary`, `quoted-print`, `base16`, `base32` and `base64`.
   */
  sealed trait ContentEncoding extends Product with Serializable

  /** The `ContentEncoding` cases and `fromString`. */
  object ContentEncoding {

    /** The `7bit` encoding, written as `sevenbit`. */
    case object SevenBit extends ContentEncoding

    /** The `8bit` encoding, written as `eightbit`. */
    case object EightBit extends ContentEncoding

    /** The `binary` encoding. */
    case object Binary extends ContentEncoding

    /**
     * The quoted-printable encoding, written as `quotedprintable` and read from
     * `quoted-print`.
     */
    case object QuotedPrintable extends ContentEncoding

    /** The `base16` encoding. */
    case object Base16 extends ContentEncoding

    /** The `base32` encoding. */
    case object Base32 extends ContentEncoding

    /** The `base64` encoding. */
    case object Base64 extends ContentEncoding

    /**
     * Returns the encoding for `7bit`, `8bit`, `binary`, `quoted-print`,
     * `base16`, `base32` or `base64`, in any case, or `None` for another
     * string, such as `quoted-printable` or `sevenbit`.
     */
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

  /** A `\$ref` link to another schema, such as `#/components/schemas/Pet`. */
  final case class RefSchema(ref: java.lang.String) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(ref = Some(ref))
  }

  /**
   * A `oneOf` schema: a value must match exactly one of the schemas in `oneOf`.
   */
  final case class OneOfSchema(oneOf: Chunk[JsonSchema]) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        oneOf = Some(oneOf.map(_.toSerializableSchema)),
      )
  }

  /** An `allOf` schema: a value must match every schema in `allOf`. */
  final case class AllOfSchema(allOf: Chunk[JsonSchema]) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        allOf = Some(allOf.map(_.toSerializableSchema)),
      )
  }

  /**
   * An `anyOf` schema: a value must match at least one of the schemas in
   * `anyOf`.
   */
  final case class AnyOfSchema(anyOf: Chunk[JsonSchema]) extends JsonSchema {

    /**
     * Removes duplicate alternatives and object alternatives that another
     * object alternative covers, and returns the result. Only the leading run
     * of `Object` alternatives is checked: an object is removed when its
     * properties equal the non-nullable properties of an object in that run,
     * itself included, so an object with no nullable property is removed; each
     * object that stays keeps only non-nullable properties in `required`. When
     * one alternative is left it is returned on its own; otherwise the result
     * is an `AnyOfSchema`, which is empty for an `AnyOfSchema` of one object
     * with no nullable property.
     */
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

  /**
   * A `number` schema in the format `float` or `double`. `exclusiveMinimum` and
   * `exclusiveMaximum` take a boolean (`Left`) or a bound (`Right`).
   * `multipleOf` is not written to the JSON output.
   */
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

  /** Builds `Number` schemas from ZIO Schema validations. */
  object Number {

    /**
     * Returns a `Number` schema in `format` with the bounds that `validation`
     * sets: `greaterThan` becomes `exclusiveMinimum` and `lessThan` becomes
     * `exclusiveMaximum`. Parts joined with `||`, such as those of
     * `Validation.between`, add no bound, and an inverted (`!`) `Float`,
     * `Double` or `BigDecimal` bound throws a
     * `java.lang.IllegalArgumentException`.
     */
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

  /** The `format` of a `Number` schema: `float` or `double`. */
  sealed trait NumberFormat extends Product with Serializable

  /** The `NumberFormat` cases and `fromString`. */
  object NumberFormat {

    /**
     * Returns `Float` for `float` and `Double` for `double`. Another string,
     * such as `decimal` or `Double`, throws a
     * `java.lang.IllegalArgumentException` (`Unknown number format: decimal`).
     */
    def fromString(string: java.lang.String): NumberFormat =
      string match {
        case "float"  => Float
        case "double" => Double
        case _        => throw new IllegalArgumentException(s"Unknown number format: $string")
      }

    /** The `float` format. */
    case object Float extends NumberFormat

    /** The `double` format. */
    case object Double extends NumberFormat

  }

  /**
   * An `integer` schema in the format `int32`, `int64` or `timestamp`.
   * `exclusiveMinimum` and `exclusiveMaximum` take a boolean (`Left`) or a
   * bound (`Right`). `multipleOf` is not written to the JSON output.
   */
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

  /**
   * Returns the leaf predicates of a ZIO Schema validation that JSON Schema
   * keywords can express. Both sides of `&&` are kept, `||` gives nothing, and
   * so does a predicate such as `equalTo`. Inside `!` (or with `not = true`),
   * `maxLength(n)` becomes `minLength(n)`, `minLength(n)` becomes
   * `maxLength(n)`, an `Int` bound `greaterThan(n)` becomes `lessThan(n + 1)`
   * and `lessThan(n)` becomes `greaterThan(n - 1)`, and a `regex` is kept as it
   * is; an inverted `Float`, `Double` or `BigDecimal` bound throws a
   * `java.lang.IllegalArgumentException`, and an inverted `Long`, `Short` or
   * `BigInt` bound throws a `java.lang.ClassCastException`.
   */
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
      case Bool.Leaf(Predicate.Str.Matches(r))                                                                  =>
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

  /** Builds `Integer` schemas from ZIO Schema validations. */
  object Integer {

    /**
     * Returns an `Integer` schema in `format` with the bounds that `validation`
     * sets: `greaterThan` becomes `exclusiveMinimum` and `lessThan` becomes
     * `exclusiveMaximum`. Parts joined with `||`, such as those of
     * `Validation.between`, add no bound. Inverted (`!`) bounds follow
     * `flatten`.
     */
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

  /** The `format` of an `Integer` schema: `int32`, `int64` or `timestamp`. */
  sealed trait IntegerFormat extends Product with Serializable

  /** The `IntegerFormat` cases and `fromString`. */
  object IntegerFormat {

    /**
     * Returns `Int32` for `int32`, `Int64` for `int64` and `Timestamp` for
     * `timestamp`. Another string throws a
     * `java.lang.IllegalArgumentException`, such as
     * `Unknown integer format: int8`.
     */
    def fromString(string: java.lang.String): IntegerFormat =
      string match {
        case "int32"     => Int32
        case "int64"     => Int64
        case "timestamp" => Timestamp
        case _           => throw new IllegalArgumentException(s"Unknown integer format: $string")
      }

    /** The `int32` format. */
    case object Int32 extends IntegerFormat

    /** The `int64` format. */
    case object Int64 extends IntegerFormat

    /** The `timestamp` format. */
    case object Timestamp extends IntegerFormat
  }

  // TODO: Add string formats and patterns
  /**
   * A `string` schema with an optional `format`, `pattern`, `maxLength` and
   * `minLength`.
   */
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

  /** Constructors of `String` schemas. */
  object String {

    /**
     * Returns a `String` schema in `format`, with no pattern and no length
     * bounds.
     */
    def apply(format: StringFormat): String = String(Some(format), None)

    /**
     * Returns a `String` schema with `pattern`, with no format and no length
     * bounds.
     */
    def apply(pattern: Pattern): String = String(None, Some(pattern))

    /**
     * Returns a `String` schema with no format, pattern or length bounds:
     * `{"type":"string"}`.
     */
    def apply(): String = String(None, None)

    /**
     * Returns a `String` schema with the `pattern`, `maxLength` and `minLength`
     * that `validation` sets, or a plain `String` schema for `None`. Parts
     * joined with `||` add nothing, and inverted (`!`) parts follow `flatten`.
     */
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

  /** The `format` of a `String` schema. */
  sealed trait StringFormat extends Product with Serializable {

    /** The format name written to the JSON output, such as `date-time`. */
    def value: java.lang.String
  }

  /** The `StringFormat` cases and `fromString`. */
  object StringFormat {

    /** A format with any other name, such as `zip`. */
    case class Custom(value: java.lang.String) extends StringFormat

    /** The `date` format. */
    case object Date extends StringFormat { val value = "date" }

    /** The `date-time` format. */
    case object DateTime extends StringFormat { val value = "date-time" }

    /** The `duration` format. */
    case object Duration extends StringFormat { val value = "duration" }

    /** The `email` format. */
    case object Email extends StringFormat { val value = "email" }

    /** The `hostname` format. */
    case object Hostname extends StringFormat { val value = "hostname" }

    /** The `idn-email` format. */
    case object IdnEmail extends StringFormat { val value = "idn-email" }

    /** The `idn-hostname` format. */
    case object IdnHostname extends StringFormat { val value = "idn-hostname" }

    /** The `ipv4` format. */
    case object IPv4 extends StringFormat { val value = "ipv4" }

    /** The `ipv6` format. */
    case object IPv6 extends StringFormat { val value = "ipv6" }

    /** The `iri` format. */
    case object IRI extends StringFormat { val value = "iri" }

    /** The `iri-reference` format. */
    case object IRIReference extends StringFormat { val value = "iri-reference" }

    /** The `json-pointer` format. */
    case object JSONPointer extends StringFormat { val value = "json-pointer" }

    /** The `password` format. */
    case object Password extends StringFormat { val value = "password" }

    /** The `regex` format. */
    case object Regex extends StringFormat { val value = "regex" }

    /** The `relative-json-pointer` format. */
    case object RelativeJSONPointer extends StringFormat { val value = "relative-json-pointer" }

    /** The `time` format. */
    case object Time extends StringFormat { val value = "time" }

    /** The `uri` format. */
    case object URI extends StringFormat { val value = "uri" }

    /** The `uri-reference` format. */
    case object URIRef extends StringFormat { val value = "uri-reference" }

    /** The `uri-template` format. */
    case object URITemplate extends StringFormat { val value = "uri-template" }

    /** The `uuid` format. */
    case object UUID extends StringFormat { val value = "uuid" }

    /**
     * Returns the case whose `value` is `string`, or `Custom(string)` for
     * another string. The match is case-sensitive: `UUID` gives
     * `Custom("UUID")`.
     */
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

  /** A regular expression for the `pattern` keyword of a `String` schema. */
  final case class Pattern(value: java.lang.String) extends Product with Serializable

  /** The `boolean` schema: `{"type":"boolean"}`. */
  case object Boolean extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(schemaType = Some(TypeOrTypes.Type("boolean")))
  }

  /**
   * An `array` schema. `items` is the schema of the elements (left out when
   * `None`), `minItems` the minimum length, and `uniqueItems = true` writes
   * `"uniqueItems":true`, while `false` writes nothing.
   */
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

  /**
   * An `object` schema with named `properties`, the names in `required`, and
   * `additionalProperties`: `Left(true)` writes nothing, `Left(false)` writes
   * `false`, and `Right(schema)` gives the schema of the values of a map.
   * `properties` is always written, as `{}` when empty, and a property whose
   * schema is nullable is left out of the written `required` list.
   */
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

    /**
     * Adds each `(name, schema)` pair as a property; for a repeated name the
     * last schema is kept. When `schema` is an `Object` whose
     * `additionalProperties` is a `Left`, with no annotation, its properties
     * and `required` names are merged into this object instead, `name` is not
     * used, and this object keeps its own `additionalProperties`.
     */
    def addAll(value: Chunk[(java.lang.String, JsonSchema)]): Object =
      value.foldLeft(this) { case (obj, (name, schema)) =>
        schema match {
          case thatObj @ Object(properties, additionalProperties, required) if thatObj.isClosedDictionary =>
            obj.copy(
              properties = obj.properties ++ properties,
              additionalProperties = combineAdditionalProperties(obj.additionalProperties, additionalProperties),
              required = obj.required ++ required,
            )
          case schema => obj.copy(properties = obj.properties + (name -> schema))
        }
      }

    /**
     * Returns a copy whose `required` list is `required`, replacing the
     * previous list. The names are not checked against the properties.
     */
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
     * In here, we reconcile 2 key schemas, after removing their annotations.
     * When either is `None`, the result is `None`. Both must be Strings, or
     * else this throws an `IllegalArgumentException`. The result is a String
     * schema that:
     *   - keeps the format only if both have the same format
     *   - keeps the pattern if both have the same pattern; if both have
     *     different patterns, joins them into one alternation built from the
     *     `toString` of each `Pattern`, such as `(Pattern(a))|(Pattern(b))`;
     *     otherwise has no pattern
     *   - takes the larger max length, or the one that is set
     *   - takes the smaller min length, or the one that is set
     *
     * @param left
     *   the key schema of the left dictionary, if any
     * @param right
     *   the key schema of the right dictionary, if any
     * @return
     *   the reconciled key schema, or `None`
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

  /** Holds the empty `Object` schema. */
  object Object {

    /**
     * An `Object` schema with no properties, nothing required and no
     * `additionalProperties` keyword: `{"type":"object","properties":{}}`.
     */
    val empty: JsonSchema.Object = JsonSchema.Object(Map.empty, Left(true), Chunk.empty)

    private[http] def extractKeySchemaFromAnnotations(js: JsonSchema): (Option[JsonSchema], Chunk[MetaData]) =
      js.annotations.foldLeft(Option.empty[JsonSchema] -> Chunk.empty[MetaData]) {
        case ((kSchemaOpt, otherAnnotations), MetaData.KeySchema(s)) => kSchemaOpt.orElse(Some(s)) -> otherAnnotations
        case ((kSchemaOpt, otherAnnotations), noKeySchemaAnnotation) =>
          kSchemaOpt -> (otherAnnotations :+ noKeySchemaAnnotation)
      }
  }

  /**
   * An `enum` schema with the allowed `values`. It is written with
   * `"type":"string"` also when the values are numbers, booleans or `null`.
   */
  final case class Enum(values: Chunk[EnumValue]) extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("string")),
        enumValues = Some(values.map(_.toJson)),
      )
  }

  /**
   * One allowed value of an `Enum`: a string, a number, a boolean, `null`, or a
   * schema written as a JSON object.
   */
  @noDiscriminator
  sealed trait EnumValue { self =>

    /**
     * Returns this value as JSON; a `SchemaValue` becomes the JSON of its
     * schema.
     */
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

  /** The `EnumValue` cases and `fromJson`. */
  object EnumValue {

    /**
     * Returns the `EnumValue` of a JSON string, number, boolean or `null`.
     * Other JSON is decoded as a schema into a `SchemaValue`; when that fails,
     * as for the array `[1]`, it throws a `java.lang.IllegalArgumentException`
     * (`Can't convert [1]`).
     */
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

    /** An enum value that is a JSON object, held as a [[JsonSchema]]. */
    final case class SchemaValue(value: JsonSchema) extends EnumValue

    /** A boolean enum value. */
    final case class Bool(value: Boolean) extends EnumValue

    /** A string enum value. */
    final case class Str(value: java.lang.String) extends EnumValue

    /** A number enum value. */
    final case class Num(value: BigDecimal) extends EnumValue

    /** The `null` enum value. */
    case object Null extends EnumValue

  }

  /** The `null` schema: `{"type":"null"}`. */
  case object Null extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema(
        schemaType = Some(TypeOrTypes.Type("null")),
      )
  }

  /** A schema that accepts any JSON value, written as `{}`. */
  case object AnyJson extends JsonSchema {
    override protected[openapi] def toSerializableSchema: SerializableJsonSchema =
      SerializableJsonSchema()
  }

}
