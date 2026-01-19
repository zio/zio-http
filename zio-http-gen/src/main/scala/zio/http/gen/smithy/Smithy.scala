package zio.http.gen.smithy

import zio.Chunk

/**
 * Smithy IDL Parser and AST
 *
 * Implements parsing of Smithy 2.0 IDL specifications as defined in:
 * https://smithy.io/2.0/spec/idl.html
 */

// =============================================================================
// AST: Node Values (JSON-like values used in traits and metadata)
// =============================================================================

sealed trait NodeValue
object NodeValue {
  case class Str(value: String)                     extends NodeValue
  case class Num(value: BigDecimal)                 extends NodeValue
  case class Bool(value: Boolean)                   extends NodeValue
  case object Null                                  extends NodeValue
  case class Arr(values: List[NodeValue])           extends NodeValue
  case class Obj(fields: List[(String, NodeValue)]) extends NodeValue
  case class ShapeIdValue(id: ShapeId)              extends NodeValue
}

// =============================================================================
// AST: Shape Identifiers
// =============================================================================

/**
 * A shape ID uniquely identifies a shape in the Smithy model. Format:
 * [namespace#]name[$ member]
 */
final case class ShapeId(
  namespace: Option[String],
  name: String,
  member: Option[String] = None,
) {
  def absolute(defaultNamespace: String): ShapeId =
    copy(namespace = Some(namespace.getOrElse(defaultNamespace)))

  def fullyQualified: String =
    namespace.fold(name)(ns => s"$ns#$name") + member.fold("")(m => s"$$$m")
}

object ShapeId {
  def apply(name: String): ShapeId                    = ShapeId(None, name, None)
  def apply(namespace: String, name: String): ShapeId = ShapeId(Some(namespace), name, None)
}

// =============================================================================
// AST: Traits
// =============================================================================

sealed trait SmithyTrait {
  def shapeId: ShapeId
}

object SmithyTrait {
  // HTTP traits
  case class Http(method: String, uri: String, code: Int = 200) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "http")
  }

  case object HttpLabel extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpLabel")
  }

  case class HttpQuery(name: String) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpQuery")
  }

  case class HttpHeader(name: String) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpHeader")
  }

  case object HttpPayload extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpPayload")
  }

  case class HttpPrefixHeaders(prefix: String) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpPrefixHeaders")
  }

  case class HttpError(code: Int) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpError")
  }

  case object HttpResponseCode extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpResponseCode")
  }

  case class HttpQueryParams() extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpQueryParams")
  }

  // Common traits
  case object Required extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "required")
  }

  case class Pattern(regex: String) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "pattern")
  }

  case class Length(min: Option[Long], max: Option[Long]) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "length")
  }

  case class Range(min: Option[BigDecimal], max: Option[BigDecimal]) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "range")
  }

  case class Documentation(value: String) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "documentation")
  }

  case object Error extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "error")
  }

  case class ErrorMessage(selector: String) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "error")
  }

  case object Readonly extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "readonly")
  }

  case object Idempotent extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "idempotent")
  }

  case class TimestampFormat(format: String) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "timestampFormat")
  }

  case class MediaType(value: String) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "mediaType")
  }

  case class JsonName(name: String) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "jsonName")
  }

  case class Default(value: NodeValue) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "default")
  }

  // Streaming trait
  case object Streaming extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "streaming")
  }

  // Event stream trait (for bi-directional streaming)
  case object EventStream extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "eventStream")
  }

  // Auth traits
  case object HttpBasicAuth extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpBasicAuth")
  }

  case object HttpDigestAuth extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpDigestAuth")
  }

  case object HttpBearerAuth extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpBearerAuth")
  }

  case object HttpApiKeyAuth extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "httpApiKeyAuth")
  }

  case class Auth(schemes: List[ShapeId]) extends SmithyTrait {
    def shapeId: ShapeId = ShapeId("smithy.api", "auth")
  }

  // Generic trait for unrecognized traits
  case class Generic(id: ShapeId, value: Option[NodeValue]) extends SmithyTrait {
    def shapeId: ShapeId = id
  }
}

// =============================================================================
// AST: Members
// =============================================================================

final case class Member(
  name: String,
  target: ShapeId,
  traits: List[SmithyTrait] = Nil,
  defaultValue: Option[NodeValue] = None,
) {
  def isRequired: Boolean = traits.exists {
    case SmithyTrait.Required => true
    case _                    => false
  }

  def httpLabel: Boolean = traits.exists {
    case SmithyTrait.HttpLabel => true
    case _                     => false
  }

  def httpQuery: Option[String] = traits.collectFirst { case SmithyTrait.HttpQuery(name) =>
    name
  }

  def httpHeader: Option[String] = traits.collectFirst { case SmithyTrait.HttpHeader(name) =>
    name
  }

  def httpPayload: Boolean = traits.exists {
    case SmithyTrait.HttpPayload => true
    case _                       => false
  }
}

// =============================================================================
// AST: Shapes
// =============================================================================

sealed trait Shape {
  def traits: List[SmithyTrait]
  def mixins: List[ShapeId]

  def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape
}

object Shape {
  // Simple shapes
  sealed trait SimpleShape extends Shape {
    def mixins: List[ShapeId] = Nil
  }

  case class BlobShape(traits: List[SmithyTrait] = Nil)       extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class BooleanShape(traits: List[SmithyTrait] = Nil)    extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class StringShape(traits: List[SmithyTrait] = Nil)     extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class ByteShape(traits: List[SmithyTrait] = Nil)       extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class ShortShape(traits: List[SmithyTrait] = Nil)      extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class IntegerShape(traits: List[SmithyTrait] = Nil)    extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class LongShape(traits: List[SmithyTrait] = Nil)       extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class FloatShape(traits: List[SmithyTrait] = Nil)      extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class DoubleShape(traits: List[SmithyTrait] = Nil)     extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class BigIntegerShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class BigDecimalShape(traits: List[SmithyTrait] = Nil) extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class TimestampShape(traits: List[SmithyTrait] = Nil)  extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
  case class DocumentShape(traits: List[SmithyTrait] = Nil)   extends SimpleShape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  // Aggregate shapes
  case class ListShape(
    member: ShapeId,
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  case class MapShape(
    key: ShapeId,
    value: ShapeId,
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  case class StructureShape(
    members: Map[String, Member],
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
    forResource: Option[ShapeId] = None,
  ) extends Shape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  case class UnionShape(
    members: Map[String, Member],
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  // Enum shapes
  case class EnumShape(
    members: Map[String, EnumMember],
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  case class IntEnumShape(
    members: Map[String, IntEnumMember],
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  case class EnumMember(
    value: Option[String],
    traits: List[SmithyTrait] = Nil,
  )

  case class IntEnumMember(
    value: Int,
    traits: List[SmithyTrait] = Nil,
  )

  // Service shapes
  case class ServiceShape(
    version: String,
    operations: List[ShapeId] = Nil,
    resources: List[ShapeId] = Nil,
    errors: List[ShapeId] = Nil,
    rename: Map[ShapeId, String] = Map.empty,
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

  case class OperationShape(
    input: Option[ShapeId],
    output: Option[ShapeId],
    errors: List[ShapeId] = Nil,
    traits: List[SmithyTrait] = Nil,
    mixins: List[ShapeId] = Nil,
  ) extends Shape {
    def httpTrait: Option[SmithyTrait.Http]                       = traits.collectFirst { case h: SmithyTrait.Http =>
      h
    }
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }

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
    def withAdditionalTraits(newTraits: List[SmithyTrait]): Shape = copy(traits = newTraits ++ traits)
  }
}

// =============================================================================
// AST: Smithy Model
// =============================================================================

final case class SmithyModel(
  version: String = "2.0",
  namespace: String = "",
  metadata: Map[String, NodeValue] = Map.empty,
  useStatements: List[ShapeId] = Nil,
  shapes: Map[String, Shape] = Map.empty,
) {
  def getShape(name: String): Option[Shape] = shapes.get(name)

  def getOperation(name: String): Option[Shape.OperationShape] =
    shapes.get(name).collect { case op: Shape.OperationShape => op }

  def getStructure(name: String): Option[Shape.StructureShape] =
    shapes.get(name).collect { case s: Shape.StructureShape => s }

  def getService(name: String): Option[Shape.ServiceShape] =
    shapes.get(name).collect { case s: Shape.ServiceShape => s }

  def allOperations: Map[String, Shape.OperationShape] =
    shapes.collect { case (name, op: Shape.OperationShape) => name -> op }

  def allServices: Map[String, Shape.ServiceShape] =
    shapes.collect { case (name, svc: Shape.ServiceShape) => name -> svc }

  def httpOperations: Map[String, Shape.OperationShape] =
    allOperations.filter { case (_, op) => op.httpTrait.isDefined }
}

// =============================================================================
// Parser - Using simple regex-based parsing
// =============================================================================

object SmithyParser {

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

    /**
     * Parse documentation comments (/// style) and return as Documentation
     * trait
     */
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

final case class Smithy(
  services: Map[String, Smithy.Service] = Map.empty,
  operations: Map[String, Smithy.Operation] = Map.empty,
  resources: Map[String, Smithy.Resource] = Map.empty,
)

object Smithy {

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

  sealed trait Aggregates
  object Aggregates {
    final case class ListAggregate(member: LegacyShapeId)                   extends Aggregates
    final case class MapAggregate(key: LegacyShapeId, value: LegacyShapeId) extends Aggregates
    final case class Structure(members: Map[String, LegacyShapeId])         extends Aggregates
    final case class Union(members: List[LegacyShapeId])                    extends Aggregates
  }

  final case class LegacyShapeId(id: String)

  final case class Service(
    version: String,
    operations: List[String],
    resources: List[String],
    errors: List[String],
    rename: Map[LegacyShapeId, String],
  )

  final case class Operation(
    name: String,
    mixins: List[String],
    input: String,
    output: String,
    errors: List[String],
  )

  sealed trait SimpleShapes
  object SimpleShapes {
    case object Blob       extends SimpleShapes
    case object Boolean    extends SimpleShapes
    case object String     extends SimpleShapes
    case object Byte       extends SimpleShapes
    case object Short      extends SimpleShapes
    case object Integer    extends SimpleShapes
    case object Long       extends SimpleShapes
    case object Float      extends SimpleShapes
    case object Double     extends SimpleShapes
    case object BigInteger extends SimpleShapes
    case object BigDecimal extends SimpleShapes
    case object Timestamp  extends SimpleShapes
    case object Document   extends SimpleShapes
    case object List       extends SimpleShapes
    case object Map        extends SimpleShapes
    case object Structure  extends SimpleShapes
    case object Union      extends SimpleShapes
    case object Service    extends SimpleShapes
    case object Operation  extends SimpleShapes
    case object Resource   extends SimpleShapes
  }
}
