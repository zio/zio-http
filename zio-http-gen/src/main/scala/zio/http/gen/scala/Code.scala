package zio.http.gen.scala

import scala.meta.Term
import scala.meta.prettyprinters.XtensionSyntax

import zio.http.gen.openapi
import zio.http.gen.openapi.Config.NormalizeFields
import zio.http.{Method, Status}

/**
 * The model of generated Scala source: files, objects, case classes, enums,
 * fields, imports and types. `EndpointGen` builds the model and `CodeGen`
 * renders it.
 */
sealed trait Code extends Product with Serializable

/**
 * The cases of `Code`.
 */
object Code {

  /**
   * A Scala type that can be the type of a field. `CodeGen.render` renders it
   * as source text.
   */
  sealed trait ScalaType extends Code { self =>

    /**
     * Returns a `Collection.Seq` of this type, rendered as `Chunk[T]`, or as
     * `NonEmptyChunk[T]` when `nonEmpty` is `true`.
     */
    def seq(nonEmpty: Boolean): Collection.Seq = Collection.Seq(self, nonEmpty)

    /**
     * Returns a `Collection.Set` of this type, rendered as `Set[T]`, or as
     * `NonEmptySet[T]` when `nonEmpty` is `true`.
     */
    def set(nonEmpty: Boolean): Collection.Set = Collection.Set(self, nonEmpty)

    /**
     * Returns a `Collection.Map` with this type as the value type and `String`
     * keys, rendered as `Map[String, T]`.
     */
    def map: Collection.Map = Collection.Map(self, None)

    /**
     * Returns a `Collection.Opt` of this type, rendered as `Option[T]`.
     */
    def opt: Collection.Opt = Collection.Opt(self)
  }

  /**
   * The `ScalaType` cases for an inferred type, `Unit`, JSON values and unions
   * of two types.
   */
  object ScalaType {

    /**
     * No explicit type: a field of this type is rendered as `val name`, without
     * a type annotation.
     */
    case object Inferred extends ScalaType

    /**
     * The `Unit` type, used for a `null` schema. `CodeGen.render` has no
     * rendering for it and throws an exception; `Primitive.ScalaUnit` is
     * rendered as `Unit`.
     */
    case object Unit extends ScalaType

    /**
     * The JSON value type, rendered as `Json` with the import
     * `zio.json.ast.Json`.
     */
    case object JsonAST extends ScalaType

    /**
     * A union of the types `left` and `right`. `CodeGen.render` has no
     * rendering for it and throws an exception.
     */
    final case class Or(left: ScalaType, right: ScalaType) extends ScalaType
  }

  /**
   * A reference to a type by name, rendered as the name itself.
   */
  final case class TypeRef(name: String) extends ScalaType

  /**
   * The source files to generate, as returned by `EndpointGen.fromOpenAPI`.
   * Render them with `CodeGen.renderedFiles` or `CodeGen.writeFiles`;
   * `CodeGen.render` throws an exception for a `Files` value.
   */
  final case class Files(files: List[File]) extends Code

  /**
   * A Scala source file.
   *
   * @param path
   *   the path of the file, as directory names followed by the file name
   * @param pkgPath
   *   the package segments that follow the base package in the `package` clause
   * @param imports
   *   the imports that the file needs
   * @param objects
   *   the objects that the file defines
   * @param caseClasses
   *   the case classes that the file defines
   * @param enums
   *   the enums that the file defines
   */
  final case class File(
    path: List[String],
    pkgPath: List[String],
    imports: List[Import],
    objects: List[Object],
    caseClasses: List[CaseClass],
    enums: List[Enum],
  ) extends Code

  /**
   * An import clause.
   */
  sealed trait Import extends Code

  /**
   * The constructors of `Import`.
   */
  object Import {

    /**
     * Returns an `Absolute` import of `name`.
     */
    def apply(name: String): Import = Absolute(name)

    /**
     * An import rendered as `import <path>`.
     */
    final case class Absolute(path: String) extends Import

    /**
     * An import rendered as `import <basePackage>.<path>`, where `basePackage`
     * is the base package given to `CodeGen`.
     */
    final case class FromBase(path: String) extends Import
  }

  /**
   * A Scala `object`.
   *
   * @param name
   *   the name of the object
   * @param extensions
   *   the types that the object extends, rendered as `extends A with B`
   * @param schema
   *   how the implicit schema of the object is defined, if it has one
   * @param endpoints
   *   the endpoint definitions of the object, by the name of the field
   * @param objects
   *   the nested objects
   * @param caseClasses
   *   the nested case classes
   * @param enums
   *   the nested enums
   */
  final case class Object(
    name: String,
    extensions: List[String],
    schema: Option[Object.SchemaCode],
    endpoints: Map[Field, EndpointCode],
    objects: List[Object],
    caseClasses: List[CaseClass],
    enums: List[Enum],
  ) extends ScalaType

  /**
   * The schema definitions and constructors of `Object`.
   */
  object Object {

    /**
     * This is a means to provide implicit codec/schema in different ways. e.g.
     * deriving with macros, or manual transforming on a primitive type.
     */
    sealed trait SchemaCode {

      /**
       * Appends the definition of the implicit schema of `typeName` to `sb`.
       */
      def codecLineWithStringBuilder(typeName: String, sb: StringBuilder): Unit
    }

    /**
     * The cases of `SchemaCode`.
     */
    object SchemaCode {

      /**
       * Defines the schema as
       * `implicit val codec: Schema[T] = DeriveSchema.gen[T]`.
       */
      case object DeriveSchemaGen extends SchemaCode {
        override def codecLineWithStringBuilder(typeName: String, sb: StringBuilder): Unit = {
          sb ++= " implicit val codec: Schema["
          sb ++= typeName
          sb ++= "] = DeriveSchema.gen["
          sb ++= typeName
          sb += ']'
        }
      }

      /**
       * Defines the schema of a newtype over `primitiveType` as
       * `implicit val schema: Schema[T.Type] = Schema.primitive[P].transform(wrap, unwrap)`.
       */
      case class AliasedNewtype(primitiveType: String) extends SchemaCode {
        override def codecLineWithStringBuilder(typeName: String, sb: StringBuilder): Unit = {
          sb ++= " implicit val schema: Schema["
          sb ++= typeName
          sb ++= ".Type] = Schema.primitive["
          sb ++= primitiveType
          sb ++= "].transform(wrap, unwrap)"
        }
      }
    }

    /**
     * Returns an `Object` with the given members whose schema is derived with
     * `SchemaCode.DeriveSchemaGen`.
     */
    def withDefaultSchemaDerivation(
      name: String,
      extensions: List[String],
      endpoints: Map[Field, EndpointCode],
      objects: List[Object],
      caseClasses: List[CaseClass],
      enums: List[Enum],
    ): Object =
      Object(name, extensions, Some(SchemaCode.DeriveSchemaGen), endpoints, objects, caseClasses, enums)

    /**
     * Returns an object named `str` with a derived schema and no extensions,
     * endpoints or nested members.
     */
    def schemaCompanion(str: String): Object = withDefaultSchemaDerivation(
      name = str,
      extensions = Nil,
      endpoints = Map.empty,
      objects = Nil,
      caseClasses = Nil,
      enums = Nil,
    )

    /**
     * Returns an object with the given endpoints, no extensions, no schema and
     * no nested members.
     */
    def apply(name: String, endpoints: Map[Field, EndpointCode]): Object =
      new Object(
        name = name,
        extensions = Nil,
        schema = None,
        endpoints = endpoints,
        objects = Nil,
        caseClasses = Nil,
        enums = Nil,
      )
  }

  /**
   * A Scala case class.
   *
   * @param name
   *   the name of the case class
   * @param fields
   *   the fields of the case class
   * @param companionObject
   *   the companion object, if there is one
   * @param mixins
   *   the types that the case class extends, rendered as `extends A with B`
   */
  final case class CaseClass(name: String, fields: List[Field], companionObject: Option[Object], mixins: List[String])
      extends ScalaType

  /**
   * The constructors of `CaseClass`.
   */
  object CaseClass {

    /**
     * Returns a case class with the given mixins, no fields and no companion
     * object.
     */
    def apply(name: String, mixins: List[String]): CaseClass = CaseClass(name, Nil, None, mixins)
  }

  /**
   * A sealed trait with its cases, rendered with a companion object that holds
   * the cases.
   *
   * @param name
   *   the name of the sealed trait
   * @param cases
   *   the case classes that extend the trait
   * @param caseNames
   *   the values of the `@caseName` annotations of the cases, in the order of
   *   `cases`; no annotations are rendered when it is empty
   * @param discriminator
   *   the value of the `@discriminatorName` annotation, if defined
   * @param noDiscriminator
   *   whether the trait gets a `@noDiscriminator` annotation
   * @param schema
   *   whether the companion object gets an implicit schema derived with
   *   `DeriveSchema.gen`
   * @param abstractMembers
   *   the fields that are rendered as abstract `def` members of the trait
   */
  final case class Enum(
    name: String,
    cases: List[CaseClass],
    caseNames: List[String] = Nil,
    discriminator: Option[String] = None,
    noDiscriminator: Boolean = false,
    schema: Boolean = true,
    abstractMembers: List[Field] = Nil,
  ) extends ScalaType

  /**
   * An annotation on a field.
   *
   * @param value
   *   the source text of the annotation
   * @param imports
   *   the imports that the annotation needs
   */
  final case class Annotation(value: String, imports: List[Code.Import]) extends Code

  /**
   * A field of a case class, or an abstract member of a sealed trait.
   *
   * Create fields with the `Field.apply` methods: they turn `name` into a valid
   * Scala term name, wrapping it in backticks when needed, and may normalize
   * it.
   */
  sealed abstract case class Field private (name: String, fieldType: ScalaType, annotations: List[Annotation])
      extends Code {
    // only allow copy on fieldType, since name is mangled to be valid in smart constructor
    /**
     * Returns a copy with another `fieldType` or `annotations`. The name cannot
     * be changed.
     */
    def copy(fieldType: ScalaType = fieldType, annotations: List[Annotation] = annotations): Field =
      new Field(name, fieldType, annotations) {}
  }

  /**
   * The constructors of `Field`.
   */
  object Field {

    /**
     * Returns a field named `name` of type `ScalaType.Inferred`, with name
     * normalization disabled.
     */
    def apply(name: String): Field =
      apply(name, ScalaType.Inferred)

    /**
     * Returns a field named `name` of type `ScalaType.Inferred`, using `conf`
     * to normalize the name.
     */
    def apply(name: String, conf: NormalizeFields): Field =
      apply(name, ScalaType.Inferred, conf)

    /**
     * Returns a field named `name` of type `fieldType`, with name normalization
     * disabled.
     */
    def apply(name: String, fieldType: ScalaType): Field =
      apply(name, fieldType, openapi.Config.default.fieldNamesNormalization)

    /**
     * Returns a field named `name` of type `fieldType`, using `conf` to
     * normalize the name.
     */
    def apply(name: String, fieldType: ScalaType, conf: NormalizeFields): Field =
      apply(name, fieldType, Nil, conf)

    /**
     * Returns a field named `name` of type `fieldType` with the single
     * annotation `annotation`, using `conf` to normalize the name.
     */
    def apply(name: String, fieldType: ScalaType, annotation: Annotation, conf: NormalizeFields): Field =
      apply(name, fieldType, List(annotation), conf)

    /**
     * Returns a field named `name` of type `fieldType` with `annotations`,
     * using `conf` to normalize the name.
     *
     * A name that is a key of `conf.manualOverrides` is replaced by its entry.
     * Otherwise, when `conf.enableAutomatic` is set, the name is replaced by
     * `normalize(name)` if that returns a name. A replaced name adds the
     * annotation `@fieldName("<original name>")`, unless the replacement is not
     * a valid Scala term name, in which case the original name is kept. The
     * annotations are sorted by their source text.
     */
    def apply(name: String, fieldType: ScalaType, annotations: List[Annotation], conf: NormalizeFields): Field = {

      def mkValidScalaTermName(term: String) = Term.Name(term).syntax

      val (validScalaTermName, originalFieldNameAnnotation) = conf.manualOverrides
        .get(name)
        .orElse(if (conf.enableAutomatic) normalize(name) else None)
        .fold(mkValidScalaTermName(name) -> Option.empty[Annotation]) { maybeValidScala =>
          val valid = mkValidScalaTermName(maybeValidScala)
          // if modified name is an invalid scala term,
          // then no reason to use backticks wrapped non-original name.
          // In this case we return the original name,
          // after possibly wrapping with backticks.
          if (valid != maybeValidScala) mkValidScalaTermName(name) -> Option.empty[Annotation]
          else {
            val annotationString = "@fieldName(\"" + name + "\")"
            val annotationImport = List(Import("zio.schema.annotation.fieldName"))
            maybeValidScala -> Some(Annotation(annotationString, annotationImport))
          }
        }

      val allAnnotations = originalFieldNameAnnotation.fold(annotations)(annotations.::)
      new Field(validScalaTermName, fieldType, allAnnotations.sortBy(_.value)) {}
    }

    private val regex = "(?<=[a-z0-9])(?=[A-Z0-9])|(?<=[A-Z0-9])(?=[A-Z0-9][a-z0-9])|[^a-zA-Z0-9]+"

    /**
     * Returns `name` converted to camelCase, or `None` when there is nothing to
     * change.
     *
     * The name is split at characters that are not letters or digits and at
     * lower-to-upper case boundaries. The first part is lowercased and the
     * other parts are capitalized. For example, `foo_bar` gives `Some(fooBar)`
     * and `fooBar` gives `None`.
     */
    def normalize(name: String): Option[String] = {

      name
        .split(regex)
        .toList match {
        case Nil          => None
        case head :: tail =>
          val normalized = (head.toLowerCase :: tail.map(_.capitalize)).mkString
          // no need to normalize if the name is already normalized
          // returning None here will signal there's no need for annotation.
          if (normalized == name) None
          else Some(normalized)
      }
    }
  }

  /**
   * A collection type.
   */
  sealed trait Collection extends ScalaType {

    /**
     * The type of the elements; for a `Map`, the type of the values.
     */
    def elementType: ScalaType
  }

  /**
   * The cases of `Collection`.
   */
  object Collection {

    /**
     * A sequence, rendered as `Chunk[T]`, or as `NonEmptyChunk[T]` when
     * `nonEmpty` is `true`.
     */
    final case class Seq(elementType: ScalaType, nonEmpty: Boolean) extends Collection

    /**
     * A set, rendered as `Set[T]`, or as `NonEmptySet[T]` when `nonEmpty` is
     * `true`.
     */
    final case class Set(elementType: ScalaType, nonEmpty: Boolean) extends Collection

    /**
     * A map with values of type `elementType`, rendered as `Map[K, V]`. The key
     * type is `keysType`, or `String` when it is `None`.
     */
    final case class Map(elementType: ScalaType, keysType: Option[ScalaType]) extends Collection

    /**
     * An optional value, rendered as `Option[T]`.
     */
    final case class Opt(elementType: ScalaType) extends Collection
  }

  /**
   * A primitive type, rendered by its simple name.
   */
  sealed trait Primitive extends ScalaType

  /**
   * The cases of `Primitive`.
   */
  object Primitive {

    /**
     * `Int`.
     */
    case object ScalaInt extends Primitive

    /**
     * `Long`.
     */
    case object ScalaLong extends Primitive

    /**
     * `Double`.
     */
    case object ScalaDouble extends Primitive

    /**
     * `Float`.
     */
    case object ScalaFloat extends Primitive

    /**
     * `Char`.
     */
    case object ScalaChar extends Primitive

    /**
     * `Byte`.
     */
    case object ScalaByte extends Primitive

    /**
     * `Short`.
     */
    case object ScalaShort extends Primitive

    /**
     * `Boolean`.
     */
    case object ScalaBoolean extends Primitive

    /**
     * `Unit`.
     */
    case object ScalaUnit extends Primitive

    /**
     * `java.util.UUID`, rendered with the import `java.util.UUID`.
     */
    case object ScalaUUID extends Primitive

    /**
     * `java.time.LocalDate`, rendered with the import `java.time.LocalDate`.
     */
    case object ScalaLocalDate extends Primitive

    /**
     * `java.time.Instant`, rendered with the import `java.time.Instant`.
     */
    case object ScalaInstant extends Primitive

    /**
     * `java.time.LocalTime`, rendered with the import `java.time.LocalTime`.
     */
    case object ScalaTime extends Primitive

    /**
     * `java.time.Duration`. `CodeGen.render` has no rendering for it and throws
     * an exception.
     */
    case object ScalaDuration extends Primitive

    /**
     * `String`.
     */
    case object ScalaString extends Primitive
  }

  /**
   * The description of one endpoint, rendered as an `Endpoint(...)` expression.
   *
   * @param method
   *   the HTTP method
   * @param pathPatternCode
   *   the path of the endpoint
   * @param queryParamsCode
   *   the query parameters
   * @param headersCode
   *   the headers
   * @param inCode
   *   the request body
   * @param outCodes
   *   the responses
   * @param errorsCode
   *   the error responses
   * @param authTypeCode
   *   the authentication type, if any
   */
  final case class EndpointCode(
    method: Method,
    pathPatternCode: PathPatternCode,
    queryParamsCode: Set[QueryParamCode],
    headersCode: HeadersCode,
    inCode: InCode,
    outCodes: List[OutCode],
    errorsCode: List[OutCode],
    authTypeCode: Option[AuthTypeCode],
  ) extends Code

  /**
   * The path of an endpoint, as a list of segments.
   */
  final case class PathPatternCode(segments: List[PathSegmentCode])

  /**
   * A segment of a path: a literal, or a variable named `name` whose values
   * have the type `segmentType`.
   */
  final case class PathSegmentCode(name: String, segmentType: CodecType)

  /**
   * The constructors of `PathSegmentCode`.
   */
  object PathSegmentCode {

    /**
     * Returns a literal segment with the text `name`.
     */
    def apply(name: String): PathSegmentCode = PathSegmentCode(name, CodecType.Literal)
  }

  /**
   * The type of a path segment or of a query parameter.
   */
  sealed trait CodecType

  /**
   * The cases of `CodecType`.
   */
  object CodecType {

    /**
     * A `Boolean` value.
     */
    case object Boolean extends CodecType

    /**
     * An `Int` value.
     */
    case object Int extends CodecType

    /**
     * A literal text. It is valid only as a path segment; rendering it as a
     * query parameter throws an exception.
     */
    case object Literal extends CodecType

    /**
     * A `Long` value.
     */
    case object Long extends CodecType

    /**
     * A `String` value.
     */
    case object String extends CodecType

    /**
     * A `java.util.UUID` value.
     */
    case object UUID extends CodecType

    /**
     * A `java.time.LocalDate` value.
     */
    case object LocalDate extends CodecType

    /**
     * A `java.time.LocalTime` value.
     */
    case object LocalTime extends CodecType

    /**
     * A `java.time.Duration` value.
     */
    case object Duration extends CodecType

    /**
     * A `java.time.Instant` value.
     */
    case object Instant extends CodecType

    /**
     * A newtype named `newtypeName` over the type `underlying`. It is imported
     * from the `components` package of the base package.
     */
    case class Aliased(underlying: CodecType, newtypeName: String) extends CodecType

    /**
     * A collection of `elementType` values, rendered in query parameters as
     * `Chunk[T]` or, when `nonEmpty` is `true`, `NonEmptyChunk[T]`. Rendering
     * it as a path segment throws an exception.
     */
    case class SeqOf(elementType: CodecType, nonEmpty: Boolean) extends CodecType

    /**
     * A collection of `elementType` values, rendered in query parameters like
     * `SeqOf`, as `Chunk[T]` or `NonEmptyChunk[T]`. Rendering it as a path
     * segment throws an exception.
     */
    case class SetOf(elementType: CodecType, nonEmpty: Boolean) extends CodecType
  }

  /**
   * A query parameter named `name` whose values have the type `queryType`.
   */
  final case class QueryParamCode(name: String, queryType: CodecType)

  /**
   * The headers of an endpoint.
   */
  final case class HeadersCode(headers: List[HeaderCode])

  /**
   * The constructors of `HeadersCode`.
   */
  object HeadersCode { val empty: HeadersCode = HeadersCode(Nil) }

  /**
   * A header with the given name.
   */
  final case class HeaderCode(name: String)

  /**
   * The request body of an endpoint.
   *
   * @param inType
   *   the Scala type of the body, as source text
   * @param name
   *   the name of the body, if any
   * @param doc
   *   the documentation of the body, if any
   * @param streaming
   *   whether the body is a stream
   */
  final case class InCode(
    inType: String,
    name: Option[String],
    doc: Option[String],
    streaming: Boolean,
  )

  /**
   * The constructors of `InCode`.
   */
  object InCode { def apply(inType: String): InCode = InCode(inType, None, None, streaming = false) }

  /**
   * A response of an endpoint.
   *
   * @param outType
   *   the Scala type of the body, as source text
   * @param status
   *   the HTTP status
   * @param mediaType
   *   the media type of the body, if known; `CodeGen` does not use it when
   *   rendering
   * @param doc
   *   the documentation of the response, if any
   * @param streaming
   *   whether the body is a stream
   */
  final case class OutCode(
    outType: String,
    status: Status,
    mediaType: Option[String],
    doc: Option[String],
    streaming: Boolean,
  )

  /**
   * The constructors of `OutCode`.
   */
  object OutCode {

    /**
     * Returns a non-streaming response with the given type and status, and no
     * media type or documentation.
     */
    def apply(outType: String, status: Status): OutCode = OutCode(outType, status, None, None, streaming = false)

    /**
     * Returns a non-streaming response with the given type and status, the
     * media type `application/json` and no documentation.
     */
    def json(outType: String, status: Status): OutCode =
      OutCode(outType, status, Some("application/json"), None, streaming = false)
  }

  /**
   * The authentication type of an endpoint, by name. `CodeGen` renders
   * `Bearer`, `Basic` and `Digest`.
   */
  final case class AuthTypeCode(authType: String) extends Code
}
