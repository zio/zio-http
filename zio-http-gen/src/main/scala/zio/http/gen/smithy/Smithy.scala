package zio.http.gen.smithy

import zio.Chunk

/**
 * Smithy IDL Parser and AST
 *
 * Parses a subset of the Smithy 2.0 IDL, which is defined in:
 * https://smithy.io/2.0/spec/idl.html
 */

// =============================================================================
// AST: Node Values (JSON-like values used in traits and metadata)
// =============================================================================

/**
 * A JSON-like value in a trait argument or control statement: a string, number,
 * boolean, `null`, array, object or shape ID.
 */
sealed trait NodeValue

/**
 * The cases of `NodeValue`.
 */
object NodeValue {

  /**
   * A string value.
   */
  case class Str(value: String) extends NodeValue

  /**
   * A number value, held as a `BigDecimal`.
   */
  case class Num(value: BigDecimal) extends NodeValue

  /**
   * A boolean value.
   */
  case class Bool(value: Boolean) extends NodeValue

  /**
   * The `null` value. The parser also returns it when it finds no value to
   * read.
   */
  case object Null extends NodeValue

  /**
   * An array of values.
   */
  case class Arr(values: List[NodeValue]) extends NodeValue

  /**
   * An object, held as a list of field name and value pairs in source order.
   */
  case class Obj(fields: List[(String, NodeValue)]) extends NodeValue

  /**
   * A value written as an unquoted shape ID, such as `String` or
   * `smithy.api#String`.
   */
  case class ShapeIdValue(id: ShapeId) extends NodeValue
}

// =============================================================================
// AST: Shape Identifiers
// =============================================================================

/**
 * A shape ID identifies a shape in a Smithy model. Its text form is
 * `namespace#name`, with an optional `\$member` suffix and an optional
 * namespace. The validator and the code generator look shapes up by `name` and
 * ignore the namespace.
 */
final case class ShapeId(
  namespace: Option[String],
  name: String,
  member: Option[String] = None,
) {

  /**
   * Returns this shape ID with `defaultNamespace` as its namespace when it has
   * none. A shape ID that already has a namespace keeps it.
   */
  def absolute(defaultNamespace: String): ShapeId =
    copy(namespace = Some(namespace.getOrElse(defaultNamespace)))

  /**
   * Renders the shape ID as `namespace#name`, followed by `\$member` when a
   * member is present. Without a namespace it renders `name` only.
   */
  def fullyQualified: String =
    namespace.fold(name)(ns => s"$ns#$name") + member.fold("")(m => s"$$$m")
}

/**
 * Constructors for `ShapeId`.
 */
object ShapeId {

  /**
   * Creates a shape ID with no namespace and no member.
   */
  def apply(name: String): ShapeId = ShapeId(None, name, None)

  /**
   * Creates a shape ID with the given namespace and no member.
   */
  def apply(namespace: String, name: String): ShapeId = ShapeId(Some(namespace), name, None)
}

// =============================================================================
// AST: Traits
// =============================================================================

/**
 * A Smithy trait applied to a shape or a member. The parser turns a trait it
 * does not know, or one whose argument has an unexpected form, into
 * `SmithyTrait.Generic`.
 */
sealed trait SmithyTrait {

  /**
   * The shape ID of the trait, such as `smithy.api#http`.
   */
  def shapeId: ShapeId
}

/**
 * The traits that the parser recognizes.
 */
object SmithyTrait {
  // HTTP traits
  /**
   * The `smithy.api#http` trait: the HTTP method, the URI template and the
   * success status code. When the parser reads the trait without a `method` it
   * uses `GET`, without a `uri` it uses `/`, and without a `code` it uses 200.
   */
  case class Http(method: String, uri: String, code: Int = 200) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#http`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "http")
  }

  /**
   * The `smithy.api#httpLabel` trait: the member is bound to a label of the URI
   * template.
   */
  case object HttpLabel extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpLabel`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpLabel")
  }

  /**
   * The `smithy.api#httpQuery` trait: the member is bound to the query string
   * parameter `name`.
   */
  case class HttpQuery(name: String) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpQuery`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpQuery")
  }

  /**
   * The `smithy.api#httpHeader` trait: the member is bound to the HTTP header
   * `name`.
   */
  case class HttpHeader(name: String) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpHeader`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpHeader")
  }

  /**
   * The `smithy.api#httpPayload` trait: the member is the body of the request
   * or response.
   */
  case object HttpPayload extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpPayload`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpPayload")
  }

  /**
   * The `smithy.api#httpPrefixHeaders` trait: the member collects the headers
   * whose names start with `prefix`.
   */
  case class HttpPrefixHeaders(prefix: String) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpPrefixHeaders`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpPrefixHeaders")
  }

  /**
   * The `smithy.api#httpError` trait: the structure is an error returned with
   * the status code `code`.
   */
  case class HttpError(code: Int) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpError`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpError")
  }

  /**
   * The `smithy.api#httpResponseCode` trait: the member holds the response
   * status code.
   */
  case object HttpResponseCode extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpResponseCode`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpResponseCode")
  }

  /**
   * The `smithy.api#httpQueryParams` trait: the map member is bound to query
   * string parameters.
   */
  case class HttpQueryParams() extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpQueryParams`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpQueryParams")
  }

  // Common traits
  /**
   * The `smithy.api#required` trait: the member must be present.
   */
  case object Required extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#required`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "required")
  }

  /**
   * The `smithy.api#pattern` trait: a regular expression that string values
   * must match.
   */
  case class Pattern(regex: String) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#pattern`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "pattern")
  }

  /**
   * The `smithy.api#length` trait: optional lower and upper bounds on the
   * length of a string, list, map or blob.
   */
  case class Length(min: Option[Long], max: Option[Long]) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#length`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "length")
  }

  /**
   * The `smithy.api#range` trait: optional lower and upper bounds on a numeric
   * value.
   */
  case class Range(min: Option[BigDecimal], max: Option[BigDecimal]) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#range`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "range")
  }

  /**
   * The `smithy.api#documentation` trait: documentation text. The parser also
   * builds it from a `///` doc comment.
   */
  case class Documentation(value: String) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#documentation`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "documentation")
  }

  /**
   * The `smithy.api#error` trait: the structure is an error shape. The parser
   * produces this case for `@error` with any argument (such as `"client"`) and
   * drops the argument.
   */
  case object Error extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#error`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "error")
  }

  /**
   * A trait that holds a selector string. The parser does not produce it, and
   * its `shapeId` is `smithy.api#error`.
   */
  case class ErrorMessage(selector: String) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#error`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "error")
  }

  /**
   * The `smithy.api#readonly` trait: the operation does not modify state.
   */
  case object Readonly extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#readonly`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "readonly")
  }

  /**
   * The `smithy.api#idempotent` trait: calling the operation again has the same
   * effect as calling it once.
   */
  case object Idempotent extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#idempotent`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "idempotent")
  }

  /**
   * The `smithy.api#timestampFormat` trait: the serialization format of a
   * timestamp, such as `date-time`, `http-date` or `epoch-seconds`.
   */
  case class TimestampFormat(format: String) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#timestampFormat`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "timestampFormat")
  }

  /**
   * The `smithy.api#mediaType` trait: the media type of a string or blob value,
   * such as `application/json`.
   */
  case class MediaType(value: String) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#mediaType`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "mediaType")
  }

  /**
   * The `smithy.api#jsonName` trait: the name that the member has in JSON.
   */
  case class JsonName(name: String) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#jsonName`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "jsonName")
  }

  /**
   * The `smithy.api#default` trait: the default value of a member.
   */
  case class Default(value: NodeValue) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#default`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "default")
  }

  // Streaming trait
  /**
   * The `smithy.api#streaming` trait: the value is streamed.
   */
  case object Streaming extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#streaming`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "streaming")
  }

  // Event stream trait (for bi-directional streaming)
  /**
   * The `smithy.api#eventStream` trait: the value is a stream of events.
   * `SmithyEndpointGen` treats it like `Streaming`.
   */
  case object EventStream extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#eventStream`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "eventStream")
  }

  // Auth traits
  /**
   * The `smithy.api#httpBasicAuth` trait: the HTTP Basic authentication scheme.
   */
  case object HttpBasicAuth extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpBasicAuth`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpBasicAuth")
  }

  /**
   * The `smithy.api#httpDigestAuth` trait: the HTTP Digest authentication
   * scheme.
   */
  case object HttpDigestAuth extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpDigestAuth`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpDigestAuth")
  }

  /**
   * The `smithy.api#httpBearerAuth` trait: the HTTP Bearer authentication
   * scheme.
   */
  case object HttpBearerAuth extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpBearerAuth`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpBearerAuth")
  }

  /**
   * The `smithy.api#httpApiKeyAuth` trait: the HTTP API key authentication
   * scheme.
   */
  case object HttpApiKeyAuth extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#httpApiKeyAuth`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "httpApiKeyAuth")
  }

  /**
   * The `smithy.api#auth` trait: the shape IDs of the authentication schemes
   * that apply.
   */
  case class Auth(schemes: List[ShapeId]) extends SmithyTrait {

    /**
     * Returns the shape ID `smithy.api#auth`.
     */
    def shapeId: ShapeId = ShapeId("smithy.api", "auth")
  }

  // Generic trait for unrecognized traits
  /**
   * A trait that has no case of its own. It keeps the shape ID of the trait and
   * its optional argument.
   */
  case class Generic(id: ShapeId, value: Option[NodeValue]) extends SmithyTrait {

    /**
     * Returns the shape ID `id` of the trait.
     */
    def shapeId: ShapeId = id
  }
}

// =============================================================================
// AST: Members
// =============================================================================

/**
 * A member of a structure or union: its name, the shape it targets, its traits
 * and an optional default value. The parser never sets `defaultValue`; a
 * `@default` trait is kept in `traits`.
 */
final case class Member(
  name: String,
  target: ShapeId,
  traits: List[SmithyTrait] = Nil,
  defaultValue: Option[NodeValue] = None,
) {

  /**
   * Returns `true` when the member has the `Required` trait.
   */
  def isRequired: Boolean = traits.exists {
    case SmithyTrait.Required => true
    case _                    => false
  }

  /**
   * Returns `true` when the member has the `HttpLabel` trait.
   */
  def httpLabel: Boolean = traits.exists {
    case SmithyTrait.HttpLabel => true
    case _                     => false
  }

  /**
   * Returns the name from the first `HttpQuery` trait of the member, or `None`.
   */
  def httpQuery: Option[String] = traits.collectFirst { case SmithyTrait.HttpQuery(name) =>
    name
  }

  /**
   * Returns the name from the first `HttpHeader` trait of the member, or
   * `None`.
   */
  def httpHeader: Option[String] = traits.collectFirst { case SmithyTrait.HttpHeader(name) =>
    name
  }

  /**
   * Returns `true` when the member has the `HttpPayload` trait.
   */
  def httpPayload: Boolean = traits.exists {
    case SmithyTrait.HttpPayload => true
    case _                       => false
  }
}

// =============================================================================
// AST: Shapes
// =============================================================================

/**
 * A shape of a Smithy model. Each shape has a list of traits and a list of
 * mixin shape IDs.
 */
sealed trait Shape {

  /**
   * The traits applied to the shape.
   */
  def traits: List[SmithyTrait]

  /**
   * The shape IDs of the mixins applied to the shape. The parser never fills
   * this list.
   */
  def mixins: List[ShapeId]

  /**
   * Returns a copy of the shape with `newTraits` placed before its existing
   * traits.
   */
  def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape
}

/**
 * The cases of `Shape`.
 */
object Shape {
  // Simple shapes
  /**
   * A shape of a simple type. It has no mixins.
   */
  sealed trait SimpleShape extends Shape {

    /**
     * Returns an empty list.
     */
    def mixins: List[ShapeId] = Nil
  }

  /**
   * A `blob` shape.
   */
  case class BlobShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `boolean` shape.
   */
  case class BooleanShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `string` shape.
   */
  case class StringShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `byte` shape.
   */
  case class ByteShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `short` shape.
   */
  case class ShortShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `integer` shape.
   */
  case class IntegerShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `long` shape.
   */
  case class LongShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `float` shape.
   */
  case class FloatShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `double` shape.
   */
  case class DoubleShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `bigInteger` shape.
   */
  case class BigIntegerShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `bigDecimal` shape.
   */
  case class BigDecimalShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `timestamp` shape.
   */
  case class TimestampShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `document` shape.
   */
  case class DocumentShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  // Aggregate shapes
  /**
   * A `list` shape whose elements have the shape `member`. The parser uses
   * `String` when the list declares no `member`.
   */
  case class ListShape(
    member: ShapeId,
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `map` shape with the key shape `key` and the value shape `value`. The
   * parser uses `String` for a missing `key` or `value`.
   */
  case class MapShape(
    key: ShapeId,
    value: ShapeId,
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `structure` shape. `members` maps member names to members. The parser
   * never sets `forResource`.
   */
  case class StructureShape(
    members: Map[String, Member],
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
    forResource: Option[ShapeId] = None,
  ) extends Shape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `union` shape. `members` maps member names to members.
   */
  case class UnionShape(
    members: Map[String, Member],
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  // Enum shapes
  /**
   * An `enum` shape. `members` maps member names to enum members.
   */
  case class EnumShape(
    members: Map[String, EnumMember],
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * An `intEnum` shape with `Int` member values. The parser does not produce
   * it: an `intEnum` declaration creates no shape.
   */
  case class IntEnumShape(
    members: Map[String, IntEnumMember],
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A member of an `enum` shape. `value` is the string after `=`, and is `None`
   * when the member has no `=` part or the string is empty.
   */
  case class EnumMember(
    value: Option[String],
    traits: List[SmithyTrait] = Nil,
  )

  /**
   * A member of an `intEnum` shape with its `Int` value.
   */
  case class IntEnumMember(
    value: Int,
    traits: List[SmithyTrait] = Nil,
  )

  // Service shapes
  /**
   * A `service` shape. The parser fills `version`, `operations`, `resources`
   * and `traits`; `errors` and `rename` stay empty. `version` is the empty
   * string when the service declares none.
   */
  case class ServiceShape(
    version: String,
    operations: List[ShapeId] = Nil,
    resources: List[ShapeId] = Nil,
    errors: List[ShapeId] = Nil,
    rename: Map[ShapeId, String] = Map.empty,
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * An `operation` shape with an optional input shape, an optional output shape
   * and a list of error shapes.
   */
  case class OperationShape(
    input: Option[ShapeId],
    output: Option[ShapeId],
    errors: List[ShapeId] = Nil,
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {

    /**
     * Returns the first `SmithyTrait.Http` trait of the operation, or `None`.
     */
    def httpTrait: Option[SmithyTrait.Http] = traits.collectFirst { case h: SmithyTrait.Http =>
      h
    }

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  /**
   * A `resource` shape. The parser fills `identifiers`, `read`, `list` and
   * `traits`; the other fields keep their default values.
   */
  case class ResourceShape(
    identifiers: Map[String, ShapeId] = Map.empty,
    properties: Map[String, ShapeId] = Map.empty,
    create: Option[ShapeId] = None,
    put: Option[ShapeId] = None,
    read: Option[ShapeId] = None,
    update: Option[ShapeId] = None,
    delete: Option[ShapeId] = None,
    list: Option[ShapeId] = None,
    operations: List[ShapeId] = Nil,
    collectionOperations: List[ShapeId] = Nil,
    resources: List[ShapeId] = Nil,
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {

    /**
     * Returns a copy of the shape with `newTraits` placed before its existing
     * traits.
     */
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
}

// =============================================================================
// AST: Smithy Model
// =============================================================================

/**
 * A parsed Smithy model. `shapes` is keyed by shape name without a namespace.
 * The parser fills `version`, `namespace`, `useStatements` and `shapes`, and
 * leaves `metadata` empty.
 */
final case class SmithyModel(
  version: String = "2.0",
  namespace: String = "",
  metadata: Map[String, NodeValue] = Map.empty,
  useStatements: List[ShapeId] = Nil,
  shapes: Map[String, Shape] = Map.empty,
) {

  /**
   * Returns the shape named `name`, or `None`.
   */
  def getShape(name: String): Option[Shape] = shapes.get(name)

  /**
   * Returns the shape named `name` when it is an operation shape, or `None`.
   */
  def getOperation(name: String): Option[Shape.OperationShape] =
    shapes.get(name).collect { case op: Shape.OperationShape => op }

  /**
   * Returns the shape named `name` when it is a structure shape, or `None`.
   */
  def getStructure(name: String): Option[Shape.StructureShape] =
    shapes.get(name).collect { case s: Shape.StructureShape => s }

  /**
   * Returns the shape named `name` when it is a service shape, or `None`.
   */
  def getService(name: String): Option[Shape.ServiceShape] =
    shapes.get(name).collect { case s: Shape.ServiceShape => s }

  /**
   * Returns the operation shapes of the model, keyed by name.
   */
  def allOperations: Map[String, Shape.OperationShape] =
    shapes.collect { case (name, op: Shape.OperationShape) => name -> op }

  /**
   * Returns the service shapes of the model, keyed by name.
   */
  def allServices: Map[String, Shape.ServiceShape] =
    shapes.collect { case (name, svc: Shape.ServiceShape) => name -> svc }

  /**
   * Returns the operation shapes that have an `Http` trait, keyed by name.
   */
  def httpOperations: Map[String, Shape.OperationShape] =
    allOperations.filter { case (_, op) => op.httpTrait.isDefined }
}

// =============================================================================
// Parser - Using simple regex-based parsing
// =============================================================================

/**
 * Parses Smithy IDL text into a `SmithyModel`. It reads control statements,
 * `namespace` and `use` statements, simple shapes, and `structure`, `list`,
 * `map`, `union`, `enum`, `operation`, `service` and `resource` shapes, with
 * their traits and `///` doc comments. A declaration that starts with another
 * keyword is skipped to the end of its line.
 */
object SmithyParser {

  /**
   * Parses `input` and returns the model. Text that the parser cannot read is
   * skipped, so malformed input usually gives a `Right` with a smaller or empty
   * model. Returns `Left("Parse error: ...")` when parsing throws an exception.
   *
   * A shape that is preceded by more than one `///` line gets no
   * `Documentation` trait. A single `///` line becomes a `Documentation` trait.
   */
  def parse(input: String): Either[String, SmithyModel] = {
    try {
      Right(parseModel(input))
    } catch {
      case e: Exception => Left(s"Parse error: ${e.getMessage}")
    }
  }

  private def parseModel(input: String): SmithyModel = {
    var pos       = 0
    var version   = "2.0"
    var namespace = ""
    var shapes    = Map.empty[String, Shape]
    var uses      = List.empty[ShapeId]

    def skipWs(): Unit = {
      while (pos < input.length) {
        val c = input.charAt(pos)
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ',') {
          pos += 1
        } else if (input.startsWith("//", pos) && !input.startsWith("///", pos)) {
          // Skip line comment (but not doc comments)
          while (pos < input.length && input.charAt(pos) != '\n') pos += 1
          if (pos < input.length) pos += 1
        } else {
          return
        }
      }
    }

    // Parse documentation comments (/// style) and return as Documentation trait
    def parseDocComments(): Option[SmithyTrait.Documentation] = {
      skipWs()
      val docLines = scala.collection.mutable.ListBuffer.empty[String]
      while (pos < input.length && input.startsWith("///", pos)) {
        pos += 3 // skip ///
        // Skip leading space if present
        if (pos < input.length && input.charAt(pos) == ' ') pos += 1
        val lineStart = pos
        while (pos < input.length && input.charAt(pos) != '\n') pos += 1
        docLines += input.substring(lineStart, pos)
        if (pos < input.length) pos += 1 // skip newline
        // Skip whitespace between doc lines (but not regular comments)
        while (pos < input.length) {
          val c = input.charAt(pos)
          if (c == ' ' || c == '\t' || c == '\r') pos += 1
          else if (c == '\n') { pos += 1 }
          else return Some(SmithyTrait.Documentation(docLines.mkString("\n")))
        }
      }
      if (docLines.nonEmpty) Some(SmithyTrait.Documentation(docLines.mkString("\n")))
      else None
    }

    def consume(s: String): Boolean = {
      skipWs()
      if (input.startsWith(s, pos)) {
        pos += s.length
        true
      } else false
    }

    def parseIdentifier(): String = {
      skipWs()
      val start = pos
      if (pos < input.length && (input.charAt(pos).isLetter || input.charAt(pos) == '_')) {
        pos += 1
        while (pos < input.length && (input.charAt(pos).isLetterOrDigit || input.charAt(pos) == '_')) {
          pos += 1
        }
        input.substring(start, pos)
      } else ""
    }

    def parseNamespace(): String = {
      val first = parseIdentifier()
      if (first.isEmpty) return ""
      val parts = scala.collection.mutable.ListBuffer(first)
      while (consume(".")) {
        parts += parseIdentifier()
      }
      parts.mkString(".")
    }

    def parseQuotedString(): String = {
      skipWs()
      if (pos < input.length && input.charAt(pos) == '"') {
        pos += 1
        val sb = new StringBuilder
        while (pos < input.length && input.charAt(pos) != '"') {
          if (input.charAt(pos) == '\\' && pos + 1 < input.length) {
            pos += 1
            input.charAt(pos) match {
              case 'n'  => sb += '\n'
              case 'r'  => sb += '\r'
              case 't'  => sb += '\t'
              case '\\' => sb += '\\'
              case '"'  => sb += '"'
              case c    => sb += c
            }
            pos += 1
          } else {
            sb += input.charAt(pos)
            pos += 1
          }
        }
        if (pos < input.length) pos += 1 // skip closing quote
        sb.result()
      } else ""
    }

    def parseNumber(): BigDecimal = {
      skipWs()
      val start = pos
      if (pos < input.length && (input.charAt(pos) == '-' || input.charAt(pos).isDigit)) {
        if (input.charAt(pos) == '-') pos += 1
        while (pos < input.length && input.charAt(pos).isDigit) pos += 1
        if (pos < input.length && input.charAt(pos) == '.') {
          pos += 1
          while (pos < input.length && input.charAt(pos).isDigit) pos += 1
        }
        BigDecimal(input.substring(start, pos))
      } else BigDecimal(0)
    }

    def parseShapeId(): ShapeId = {
      val ns = parseNamespace()
      if (consume("#")) {
        val name = parseIdentifier()
        ShapeId(Some(ns), name)
      } else {
        ShapeId(None, ns) // ns is actually the name
      }
    }

    def parseNodeValue(): NodeValue = {
      skipWs()
      if (pos >= input.length) return NodeValue.Null

      input.charAt(pos) match {
        case '"'                                   => NodeValue.Str(parseQuotedString())
        case '['                                   =>
          pos += 1
          val values = scala.collection.mutable.ListBuffer.empty[NodeValue]
          skipWs()
          while (pos < input.length && input.charAt(pos) != ']') {
            val before = pos
            values += parseNodeValue()
            skipWs()
            consume(",")
            skipWs()
            // Safety: if we didn't advance, skip a char to avoid infinite loop
            if (pos == before && pos < input.length && input.charAt(pos) != ']') pos += 1
          }
          consume("]")
          NodeValue.Arr(values.toList)
        case '{'                                   =>
          pos += 1
          val fields = scala.collection.mutable.ListBuffer.empty[(String, NodeValue)]
          skipWs()
          while (pos < input.length && input.charAt(pos) != '}') {
            val before = pos
            val key    = if (input.charAt(pos) == '"') parseQuotedString() else parseIdentifier()
            skipWs()
            consume(":")
            val value  = parseNodeValue()
            fields += (key -> value)
            skipWs()
            consume(",")
            skipWs()
            // Safety: if we didn't advance, skip a char to avoid infinite loop
            if (pos == before && pos < input.length && input.charAt(pos) != '}') pos += 1
          }
          consume("}")
          NodeValue.Obj(fields.toList)
        case c if c == '-' || c.isDigit            =>
          NodeValue.Num(parseNumber())
        case 't' if input.startsWith("true", pos)  =>
          pos += 4
          NodeValue.Bool(true)
        case 'f' if input.startsWith("false", pos) =>
          pos += 5
          NodeValue.Bool(false)
        case 'n' if input.startsWith("null", pos)  =>
          pos += 4
          NodeValue.Null
        case _                                     =>
          // Try as shape ID
          val id = parseShapeId()
          if (id.name.nonEmpty) NodeValue.ShapeIdValue(id)
          else NodeValue.Null
      }
    }

    def parseTrait(): SmithyTrait = {
      consume("@")
      val id    = parseShapeId()
      val value = if (consume("(")) {
        skipWs()
        val v = if (pos < input.length && input.charAt(pos) != ')') {
          // Check if this is an implicit object (key: value style) or a simple value
          // Peek ahead to see if we have "identifier:" pattern
          val savedPos = pos
          val firstId  = parseIdentifier()
          skipWs()
          if (firstId.nonEmpty && pos < input.length && input.charAt(pos) == ':') {
            // This is implicit object syntax: key: value, key2: value2
            pos = savedPos // Reset and parse as object
            val fields = scala.collection.mutable.ListBuffer.empty[(String, NodeValue)]
            while (pos < input.length && input.charAt(pos) != ')') {
              val before  = pos
              val key     = parseIdentifier()
              skipWs()
              consume(":")
              val nodeVal = parseNodeValue()
              fields += (key -> nodeVal)
              skipWs()
              consume(",")
              skipWs()
              // Safety: if we didn't advance, skip a char
              if (pos == before && pos < input.length && input.charAt(pos) != ')') pos += 1
            }
            Some(NodeValue.Obj(fields.toList))
          } else {
            // This is a simple value - reset and parse normally
            pos = savedPos
            Some(parseNodeValue())
          }
        } else None
        skipWs()
        consume(")")
        v
      } else None

      convertTrait(id, value)
    }

    def parseTraits(): List[SmithyTrait] = {
      val traits = scala.collection.mutable.ListBuffer.empty[SmithyTrait]
      skipWs()
      // First check for doc comments (/// style)
      parseDocComments().foreach(traits += _)
      skipWs()
      while (pos < input.length && input.charAt(pos) == '@') {
        traits += parseTrait()
        skipWs()
        // Check for doc comments between traits
        parseDocComments().foreach(traits += _)
        skipWs()
      }
      traits.toList
    }

    def parseMembers(): Map[String, Member] = {
      val members = scala.collection.mutable.Map.empty[String, Member]
      consume("{")
      skipWs()
      while (pos < input.length && input.charAt(pos) != '}') {
        val before       = pos
        val memberTraits = parseTraits()
        val name         = parseIdentifier()
        if (name.isEmpty) {
          // Can't parse member name, skip char to avoid infinite loop
          if (pos < input.length && input.charAt(pos) != '}') pos += 1
        } else {
          skipWs()
          consume(":")
          val target = parseShapeId()
          members += (name -> Member(name, target, memberTraits))
          skipWs()
          consume(",")
        }
        skipWs()
        // Safety: if we didn't advance at all, skip a char
        if (pos == before && pos < input.length && input.charAt(pos) != '}') pos += 1
      }
      consume("}")
      members.toMap
    }

    // Main parsing loop
    skipWs()

    // Parse control section
    while (consume("$")) {
      val key   = parseIdentifier()
      skipWs()
      consume(":")
      val value = parseNodeValue()
      if (key == "version") {
        value match {
          case NodeValue.Str(v) => version = v
          case _                =>
        }
      }
      skipWs()
    }

    // Parse namespace
    if (consume("namespace")) {
      skipWs()
      namespace = parseNamespace()
      skipWs()
    }

    // Parse use statements
    while (consume("use")) {
      skipWs()
      uses = uses :+ parseShapeId()
      skipWs()
    }

    // Parse shapes
    while (pos < input.length) {
      val loopStart = pos
      skipWs()
      if (pos >= input.length) {
        // done
      } else {
        val traits = parseTraits()
        skipWs()
        if (pos >= input.length) {
          // done
        } else {
          val keyword = parseIdentifier()
          skipWs()

          keyword match {
            case "string" | "integer" | "long" | "short" | "byte" | "float" | "double" | "boolean" | "blob" |
                "timestamp" | "bigInteger" | "bigDecimal" | "document" =>
              val name  = parseIdentifier()
              val shape = createSimpleShape(keyword).withAdditionalTraits(traits)
              shapes += (name -> shape)

            case "structure" =>
              val name    = parseIdentifier()
              skipWs()
              val members = parseMembers()
              shapes += (name -> Shape.StructureShape(members, traits))

            case "list" =>
              val name         = parseIdentifier()
              skipWs()
              val members      = parseMembers()
              val memberTarget = members.get("member").map(_.target).getOrElse(ShapeId("String"))
              shapes += (name -> Shape.ListShape(memberTarget, traits))

            case "map" =>
              val name        = parseIdentifier()
              skipWs()
              val members     = parseMembers()
              val keyTarget   = members.get("key").map(_.target).getOrElse(ShapeId("String"))
              val valueTarget = members.get("value").map(_.target).getOrElse(ShapeId("String"))
              shapes += (name -> Shape.MapShape(keyTarget, valueTarget, traits))

            case "union" =>
              val name    = parseIdentifier()
              skipWs()
              val members = parseMembers()
              shapes += (name -> Shape.UnionShape(members, traits))

            case "operation" =>
              val name                      = parseIdentifier()
              skipWs()
              consume("{")
              skipWs()
              var opInput: Option[ShapeId]  = None
              var opOutput: Option[ShapeId] = None
              var opErrors: List[ShapeId]   = Nil

              while (pos < input.length && input.charAt(pos) != '}') {
                val prop = parseIdentifier()
                if (prop.isEmpty) {
                  // Can't parse identifier, skip to next meaningful char or end
                  if (pos < input.length && input.charAt(pos) != '}') pos += 1
                } else {
                  skipWs()
                  consume(":")
                  skipWs()
                  prop match {
                    case "input"  => opInput = Some(parseShapeId())
                    case "output" => opOutput = Some(parseShapeId())
                    case "errors" =>
                      consume("[")
                      skipWs()
                      val errs = scala.collection.mutable.ListBuffer.empty[ShapeId]
                      while (pos < input.length && input.charAt(pos) != ']') {
                        val errBefore = pos
                        errs += parseShapeId()
                        skipWs()
                        consume(",")
                        skipWs()
                        // Safety: if we didn't advance, break to avoid infinite loop
                        if (pos == errBefore && pos < input.length && input.charAt(pos) != ']') pos += 1
                      }
                      consume("]")
                      opErrors = errs.toList
                    case _        =>
                      // Skip unknown property value
                      parseNodeValue()
                  }
                  skipWs()
                  consume(",")
                }
                skipWs()
              }
              consume("}")
              shapes += (name -> Shape.OperationShape(opInput, opOutput, opErrors, traits))

            case "service" =>
              val name       = parseIdentifier()
              skipWs()
              val obj        = parseNodeValue() match {
                case o: NodeValue.Obj => o
                case _                => NodeValue.Obj(Nil)
              }
              val fieldMap   = obj.fields.toMap
              val svcVersion = fieldMap.get("version").collect { case NodeValue.Str(v) => v }.getOrElse("")
              val operations = fieldMap
                .get("operations")
                .collect { case NodeValue.Arr(ids) =>
                  ids.collect { case NodeValue.ShapeIdValue(id) => id }
                }
                .getOrElse(Nil)
              val resources  = fieldMap
                .get("resources")
                .collect { case NodeValue.Arr(ids) =>
                  ids.collect { case NodeValue.ShapeIdValue(id) => id }
                }
                .getOrElse(Nil)
              shapes += (name -> Shape.ServiceShape(svcVersion, operations, resources, Nil, Map.empty, traits))

            case "resource" =>
              val name     = parseIdentifier()
              skipWs()
              val obj      = parseNodeValue() match {
                case o: NodeValue.Obj => o
                case _                => NodeValue.Obj(Nil)
              }
              val fieldMap = obj.fields.toMap

              def getShapeId(key: String): Option[ShapeId] =
                fieldMap.get(key).collect { case NodeValue.ShapeIdValue(id) => id }

              def getIdentifiers: Map[String, ShapeId] =
                fieldMap
                  .get("identifiers")
                  .collect { case NodeValue.Obj(fields) =>
                    fields.collect { case (k, NodeValue.ShapeIdValue(id)) => k -> id }.toMap
                  }
                  .getOrElse(Map.empty)

              shapes += (name -> Shape.ResourceShape(
                identifiers = getIdentifiers,
                read = getShapeId("read"),
                list = getShapeId("list"),
                traits = traits,
              ))

            case "enum" =>
              val name    = parseIdentifier()
              skipWs()
              consume("{")
              skipWs()
              val members = scala.collection.mutable.Map.empty[String, Shape.EnumMember]
              while (pos < input.length && input.charAt(pos) != '}') {
                val before       = pos
                val memberTraits = parseTraits()
                val memberName   = parseIdentifier()
                if (memberName.isEmpty) {
                  // Can't parse member name, skip char to avoid infinite loop
                  if (pos < input.length && input.charAt(pos) != '}') pos += 1
                } else {
                  val value = if (consume("=")) {
                    skipWs()
                    parseQuotedString() match {
                      case s if s.nonEmpty => Some(s)
                      case _               => None
                    }
                  } else None
                  members += (memberName -> Shape.EnumMember(value, memberTraits))
                  skipWs()
                  consume(",")
                }
                skipWs()
                // Safety: if we didn't advance at all, skip a char
                if (pos == before && pos < input.length && input.charAt(pos) != '}') pos += 1
              }
              consume("}")
              shapes += (name -> Shape.EnumShape(members.toMap, traits))

            case "" =>
              // end of input or can't parse - skip a char to avoid infinite loop
              if (pos < input.length) pos += 1

            case _ =>
              // Skip unknown keyword - try to skip to next shape
              // Skip any remaining content until we hit a newline or end
              while (pos < input.length && input.charAt(pos) != '\n') pos += 1
          }
          skipWs()
          // Safety: if we didn't advance at all, skip a char
          if (pos == loopStart && pos < input.length) pos += 1
        }
      }
    }

    SmithyModel(version, namespace, Map.empty, uses, shapes)
  }

  private def createSimpleShape(typeName: String): Shape = typeName match {
    case "blob"       => Shape.BlobShape()
    case "boolean"    => Shape.BooleanShape()
    case "document"   => Shape.DocumentShape()
    case "string"     => Shape.StringShape()
    case "byte"       => Shape.ByteShape()
    case "short"      => Shape.ShortShape()
    case "integer"    => Shape.IntegerShape()
    case "long"       => Shape.LongShape()
    case "float"      => Shape.FloatShape()
    case "double"     => Shape.DoubleShape()
    case "bigInteger" => Shape.BigIntegerShape()
    case "bigDecimal" => Shape.BigDecimalShape()
    case "timestamp"  => Shape.TimestampShape()
    case _            => Shape.StringShape()
  }

  private def convertTrait(id: ShapeId, value: Option[NodeValue]): SmithyTrait = {
    val name = id.name
    (name, value) match {
      case ("http", Some(NodeValue.Obj(fields))) =>
        val fieldMap = fields.toMap
        val method   = fieldMap.get("method").collect { case NodeValue.Str(m) => m }.getOrElse("GET")
        val uri      = fieldMap.get("uri").collect { case NodeValue.Str(u) => u }.getOrElse("/")
        val code     = fieldMap.get("code").collect { case NodeValue.Num(c) => c.toInt }.getOrElse(200)
        SmithyTrait.Http(method, uri, code)

      case ("httpLabel", _)                                   => SmithyTrait.HttpLabel
      case ("httpQuery", Some(NodeValue.Str(n)))              => SmithyTrait.HttpQuery(n)
      case ("httpHeader", Some(NodeValue.Str(n)))             => SmithyTrait.HttpHeader(n)
      case ("httpPayload", _)                                 => SmithyTrait.HttpPayload
      case ("httpPrefixHeaders", Some(NodeValue.Str(prefix))) => SmithyTrait.HttpPrefixHeaders(prefix)
      case ("httpError", Some(NodeValue.Num(code)))           => SmithyTrait.HttpError(code.toInt)
      case ("httpResponseCode", _)                            => SmithyTrait.HttpResponseCode
      case ("httpQueryParams", _)                             => SmithyTrait.HttpQueryParams()
      case ("required", _)                                    => SmithyTrait.Required
      case ("pattern", Some(NodeValue.Str(regex)))            => SmithyTrait.Pattern(regex)
      case ("length", Some(NodeValue.Obj(fields)))            =>
        val fieldMap = fields.toMap
        val min      = fieldMap.get("min").collect { case NodeValue.Num(n) => n.toLong }
        val max      = fieldMap.get("max").collect { case NodeValue.Num(n) => n.toLong }
        SmithyTrait.Length(min, max)
      case ("range", Some(NodeValue.Obj(fields)))             =>
        val fieldMap = fields.toMap
        val min      = fieldMap.get("min").collect { case NodeValue.Num(n) => n }
        val max      = fieldMap.get("max").collect { case NodeValue.Num(n) => n }
        SmithyTrait.Range(min, max)
      case ("documentation", Some(NodeValue.Str(doc)))        => SmithyTrait.Documentation(doc)
      case ("error", _)                                       => SmithyTrait.Error
      case ("readonly", _)                                    => SmithyTrait.Readonly
      case ("idempotent", _)                                  => SmithyTrait.Idempotent
      case ("timestampFormat", Some(NodeValue.Str(format)))   => SmithyTrait.TimestampFormat(format)
      case ("mediaType", Some(NodeValue.Str(mt)))             => SmithyTrait.MediaType(mt)
      case ("jsonName", Some(NodeValue.Str(n)))               => SmithyTrait.JsonName(n)
      case ("default", Some(v))                               => SmithyTrait.Default(v)
      case ("streaming", _)                                   => SmithyTrait.Streaming
      case ("eventStream", _)                                 => SmithyTrait.EventStream
      case ("httpBasicAuth", _)                               => SmithyTrait.HttpBasicAuth
      case ("httpDigestAuth", _)                              => SmithyTrait.HttpDigestAuth
      case ("httpBearerAuth", _)                              => SmithyTrait.HttpBearerAuth
      case ("httpApiKeyAuth", _)                              => SmithyTrait.HttpApiKeyAuth
      case ("auth", Some(NodeValue.Arr(schemes)))             =>
        val schemeIds = schemes.collect { case NodeValue.ShapeIdValue(id) => id }
        SmithyTrait.Auth(schemeIds)
      case _                                                  => SmithyTrait.Generic(id, value)
    }
  }
}

// =============================================================================
// Legacy Compatibility (for existing code)
// =============================================================================

/**
 * A reduced view of a Smithy model: services, operations and resources by name,
 * with shape references turned into plain names.
 */
final case class Smithy(
  services: Map[String, Smithy.Service] = Map.empty,
  operations: Map[String, Smithy.Operation] = Map.empty,
  resources: Map[String, Smithy.Resource] = Map.empty,
)

/**
 * Constructors for `Smithy`.
 */
object Smithy {

  /**
   * Parses `value` with `SmithyParser` and converts the model to a `Smithy`.
   * Returns an empty `Smithy` when the parser returns a `Left`.
   */
  def parse(value: String): Smithy = {
    SmithyParser.parse(value) match {
      case Right(model) => fromModel(model)
      case Left(_)      => Smithy()
    }
  }

  private def fromModel(model: SmithyModel): Smithy = {
    val services = model.allServices.map { case (name, svc) =>
      name -> Service(
        version = svc.version,
        operations = svc.operations.map(_.name),
        resources = svc.resources.map(_.name),
        errors = svc.errors.map(_.name),
        rename = svc.rename.map { case (k, v) => LegacyShapeId(k.fullyQualified) -> v },
      )
    }

    val operations = model.allOperations.map { case (name, op) =>
      name -> Operation(
        name = name,
        mixins = op.mixins.map(_.name),
        input = op.input.map(_.name).getOrElse(""),
        output = op.output.map(_.name).getOrElse(""),
        errors = op.errors.map(_.name),
      )
    }

    val resources = model.shapes.collect { case (name, rs: Shape.ResourceShape) =>
      name -> Resource(
        identifiers = rs.identifiers.map { case (k, v) => k -> LegacyShapeId(v.name) },
        properties = rs.properties.map { case (k, v) => k -> LegacyShapeId(v.name) },
        create = LegacyShapeId(rs.create.map(_.name).getOrElse("")),
        put = LegacyShapeId(rs.put.map(_.name).getOrElse("")),
        read = LegacyShapeId(rs.read.map(_.name).getOrElse("")),
        update = LegacyShapeId(rs.update.map(_.name).getOrElse("")),
        delete = LegacyShapeId(rs.delete.map(_.name).getOrElse("")),
        list = LegacyShapeId(rs.list.map(_.name).getOrElse("")),
        operations = rs.operations.map(id => LegacyShapeId(id.name)),
        collectionOperations = rs.collectionOperations.map(id => LegacyShapeId(id.name)),
        resources = rs.resources.map(id => LegacyShapeId(id.name)),
      )
    }

    Smithy(services, operations, resources)
  }

  /**
   * A resource in the reduced view. A lifecycle operation that the resource
   * does not declare is a `LegacyShapeId` with an empty `id`.
   */
  final case class Resource(
    identifiers: Map[String, LegacyShapeId],
    properties: Map[String, LegacyShapeId],
    create: LegacyShapeId,
    put: LegacyShapeId,
    read: LegacyShapeId,
    update: LegacyShapeId,
    delete: LegacyShapeId,
    list: LegacyShapeId,
    operations: List[LegacyShapeId],
    collectionOperations: List[LegacyShapeId],
    resources: List[LegacyShapeId],
  )

  /**
   * A description of an aggregate shape in the reduced view. The conversion
   * from a `SmithyModel` does not create it.
   */
  sealed trait Aggregates

  /**
   * The cases of `Aggregates`.
   */
  object Aggregates {

    /**
     * A list aggregate with the element shape `member`.
     */
    final case class ListAggregate(member: LegacyShapeId) extends Aggregates

    /**
     * A map aggregate with the key shape `key` and the value shape `value`.
     */
    final case class MapAggregate(key: LegacyShapeId, value: LegacyShapeId) extends Aggregates

    /**
     * A structure aggregate with its members by name.
     */
    final case class Structure(members: Map[String, LegacyShapeId]) extends Aggregates

    /**
     * A union aggregate with its member shapes.
     */
    final case class Union(members: List[LegacyShapeId]) extends Aggregates
  }

  /**
   * A shape reference held as a string. The conversion from a `SmithyModel`
   * stores the shape name without a namespace, except in the keys of
   * `Service.rename`, which hold the fully qualified ID.
   */
  final case class LegacyShapeId(id: String)

  /**
   * A service in the reduced view: its version and the names of its operations,
   * resources and errors, plus its renames.
   */
  final case class Service(
    version: String,
    operations: List[String],
    resources: List[String],
    errors: List[String],
    rename: Map[LegacyShapeId, String],
  )

  /**
   * An operation in the reduced view. `input` and `output` are empty strings
   * when the operation declares none.
   */
  final case class Operation(
    name: String,
    mixins: List[String],
    input: String,
    output: String,
    errors: List[String],
  )

  /**
   * The kinds of shape in the reduced view.
   */
  sealed trait SimpleShapes

  /**
   * The cases of `SimpleShapes`.
   */
  object SimpleShapes {

    /**
     * The `blob` shape kind.
     */
    case object Blob extends SimpleShapes

    /**
     * The `boolean` shape kind.
     */
    case object Boolean extends SimpleShapes

    /**
     * The `string` shape kind.
     */
    case object String extends SimpleShapes

    /**
     * The `byte` shape kind.
     */
    case object Byte extends SimpleShapes

    /**
     * The `short` shape kind.
     */
    case object Short extends SimpleShapes

    /**
     * The `integer` shape kind.
     */
    case object Integer extends SimpleShapes

    /**
     * The `long` shape kind.
     */
    case object Long extends SimpleShapes

    /**
     * The `float` shape kind.
     */
    case object Float extends SimpleShapes

    /**
     * The `double` shape kind.
     */
    case object Double extends SimpleShapes

    /**
     * The `bigInteger` shape kind.
     */
    case object BigInteger extends SimpleShapes

    /**
     * The `bigDecimal` shape kind.
     */
    case object BigDecimal extends SimpleShapes

    /**
     * The `timestamp` shape kind.
     */
    case object Timestamp extends SimpleShapes

    /**
     * The `document` shape kind.
     */
    case object Document extends SimpleShapes

    /**
     * The `list` shape kind.
     */
    case object List extends SimpleShapes

    /**
     * The `map` shape kind.
     */
    case object Map extends SimpleShapes

    /**
     * The `structure` shape kind.
     */
    case object Structure extends SimpleShapes

    /**
     * The `union` shape kind.
     */
    case object Union extends SimpleShapes

    /**
     * The `service` shape kind.
     */
    case object Service extends SimpleShapes

    /**
     * The `operation` shape kind.
     */
    case object Operation extends SimpleShapes

    /**
     * The `resource` shape kind.
     */
    case object Resource extends SimpleShapes
  }
}
