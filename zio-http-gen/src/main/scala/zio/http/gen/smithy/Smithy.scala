package zio.http.gen.smithy

import Smithy._
import zio.{Chunk, NonEmptyChunk}
import zio.http.codec.RichTextCodec

//idl =
//    [WS] ControlSection MetadataSection ShapeSection
//Whitespace
//
//WS =
//    1*(SP / NL / Comment / Comma) ; whitespace
//
//Comma =
//    ","
//
//SP =
//    1*(%x20 / %x09) ; one or more spaces or tabs
//
//NL =
//    %x0A / %x0D.0A ; Newline: \n and \r\n
//
//NotNL =
//    %x09 / %x20-10FFFF ; Any character except newline
//
//BR =
//    [SP] 1*(Comment / NL) [WS]; line break followed by whitespace
//Comments
//
//Comment =
//    DocumentationComment / LineComment
//
//DocumentationComment =
//    "///" *NotNL NL
//
//LineComment =
//    "//" [(%x09 / %x20-2E / %x30-10FFF) *NotNL] NL
//    ; First character after "//" can't be "/"
//Control
//
//ControlSection =
//    *(ControlStatement)
//
//ControlStatement =
//    "$" NodeObjectKey [SP] ":" [SP] NodeValue BR
//Metadata
//
//MetadataSection =
//    *(MetadataStatement)
//
//MetadataStatement =
//    %s"metadata" SP NodeObjectKey [SP] "=" [SP] NodeValue BR
//Node values
//
//NodeValue =
//    NodeArray
//  / NodeObject
//  / Number
//  / NodeKeyword
//  / NodeStringValue
//
//NodeArray =
//    "[" [WS] *(NodeValue [WS]) "]"
//
//NodeObject =
//    "{" [WS] [NodeObjectKvp *(WS NodeObjectKvp)] [WS] "}"
//
//NodeObjectKvp =
//    NodeObjectKey [WS] ":" [WS] NodeValue
//
//NodeObjectKey =
//    QuotedText / Identifier
//
//Number =
//    [Minus] Int [Frac] [Exp]
//
//DecimalPoint =
//    %x2E ; .
//
//DigitOneToNine =
//    %x31-39 ; 1-9
//
//E =
//    %x65 / %x45 ; e E
//
//Exp =
//    E [Minus / Plus] 1*DIGIT
//
//Frac =
//    DecimalPoint 1*DIGIT
//
//Int =
//    Zero / (DigitOneToNine *DIGIT)
//
//Minus =
//    %x2D ; -
//
//Plus =
//    %x2B ; +
//
//Zero =
//    %x30 ; 0
//
//NodeKeyword =
//    %s"true" / %s"false" / %s"null"
//
//NodeStringValue =
//    ShapeId / TextBlock / QuotedText
//
//QuotedText =
//    DQUOTE *QuotedChar DQUOTE
//
//QuotedChar =
//    %x09        ; tab
//  / %x20-21     ; space - "!"
//  / %x23-5B     ; "#" - "["
//  / %x5D-10FFFF ; "]"+
//  / EscapedChar
//  / NL
//
//EscapedChar =
//    Escape (Escape / DQUOTE / %s"b" / %s"f"
//             / %s"n" / %s"r" / %s"t" / "/"
//             / UnicodeEscape)
//
//UnicodeEscape =
//    %s"u" Hex Hex Hex Hex
//
//Hex =
//    DIGIT / %x41-46 / %x61-66
//
//Escape =
//    %x5C ; backslash
//
//TextBlock =
//    ThreeDquotes [SP] NL *TextBlockContent ThreeDquotes
//
//TextBlockContent =
//    QuotedChar / (1*2DQUOTE 1*QuotedChar)
//
//ThreeDquotes =
//    DQUOTE DQUOTE DQUOTE
//Shapes
//
//ShapeSection =
//    [NamespaceStatement UseSection [ShapeStatements]]
//
//NamespaceStatement =
//    %s"namespace" SP Namespace BR
//
//UseSection =
//    *(UseStatement)
//
//UseStatement =
//    %s"use" SP AbsoluteRootShapeId BR
//
//ShapeStatements =
//    ShapeOrApplyStatement *(BR ShapeOrApplyStatement)
//
//ShapeOrApplyStatement =
//    ShapeStatement / ApplyStatement
//
//ShapeStatement =
//    TraitStatements Shape
//
//Shape =
//    SimpleShape
//  / EnumShape
//  / AggregateShape
//  / EntityShape
//  / OperationShape
//
//SimpleShape =
//    SimpleTypeName SP Identifier [Mixins]
//
//SimpleTypeName =
//    %s"blob" / %s"boolean" / %s"document" / %s"string"
//  / %s"byte" / %s"short" / %s"integer" / %s"long"
//  / %s"float" / %s"double" / %s"bigInteger"
//  / %s"bigDecimal" / %s"timestamp"
//
//Mixins =
//    [SP] %s"with" [WS] "[" [WS] 1*(ShapeId [WS]) "]"
//
//EnumShape =
//    EnumTypeName SP Identifier [Mixins] [WS] EnumShapeMembers
//
//EnumTypeName =
//    %s"enum" / %s"intEnum"
//
//EnumShapeMembers =
//    "{" [WS] 1*(EnumShapeMember [WS]) "}"
//
//EnumShapeMember =
//    TraitStatements Identifier [ValueAssignment]
//
//ValueAssignment =
//    [SP] "=" [SP] NodeValue [SP] [Comma] BR
//
//AggregateShape =
//    AggregateTypeName SP Identifier [ForResource] [Mixins]
//     [WS] ShapeMembers
//
//AggregateTypeName =
//    %s"list" / %s"map" / %s"union" / %s"structure"
//
//ForResource =
//    SP %s"for" SP ShapeId
//
//ShapeMembers =
//    "{" [WS] *(ShapeMember [WS]) "}"
//
//ShapeMember =
//    TraitStatements (ExplicitShapeMember / ElidedShapeMember)
//     [ValueAssignment]
//
//ExplicitShapeMember =
//    Identifier [SP] ":" [SP] ShapeId
//
//ElidedShapeMember =
//    "$" Identifier
//
//EntityShape =
//    EntityTypeName SP Identifier [Mixins] [WS] NodeObject
//
//EntityTypeName =
//    %s"service" / %s"resource"
//
//OperationShape =
//    %s"operation" SP Identifier [Mixins] [WS] OperationBody
//
//OperationBody =
//    "{" [WS] *(OperationProperty [WS]) "}"
//
//OperationProperty =
//    OperationInput / OperationOutput / OperationErrors
//
//OperationInput =
//    %s"input" [WS] (InlineAggregateShape / (":" [WS] ShapeId))
//
//OperationOutput =
//    %s"output" [WS] (InlineAggregateShape / (":" [WS] ShapeId))
//
//OperationErrors =
//    %s"errors" [WS] ":" [WS] "[" [WS] *(ShapeId [WS]) "]"
//
//InlineAggregateShape =
//    ":=" [WS] TraitStatements [ForResource] [Mixins]
//     [WS] ShapeMembers
//Traits
//
//TraitStatements =
//    *(Trait [WS])
//
//Trait =
//    "@" ShapeId [TraitBody]
//
//TraitBody =
//    "(" [WS] [TraitStructure / TraitNode] ")"
//
//TraitStructure =
//    1*(NodeObjectKvp [WS])
//
//TraitNode =
//    NodeValue [WS]
//
//ApplyStatement =
//    ApplyStatementSingular / ApplyStatementBlock
//
//ApplyStatementSingular =
//    %s"apply" SP ShapeId WS Trait
//
//ApplyStatementBlock =
//    %s"apply" SP ShapeId WS "{" [WS] TraitStatements "}"

final case class Smithy(
    services: Map[String, Service] = Map.empty,
    operations: Map[String, Operation] = Map.empty,
    resources: Map[String, Resource] = Map.empty,
)


object Smithy {

  lazy val ws =
    sp ~|~ nl ~|~ comment ~|~ comma
  lazy val sp = RichTextCodec.chars(' ', '\t').repeatNonEmpty.const(NonEmptyChunk(' '))
  lazy val nl = (RichTextCodec.char('\n') | RichTextCodec.literal("\r\n")).const(Left('\n'))
  lazy val notNl = RichTextCodec.filter { c =>
    c.toInt match {
      case 0x09 => true
      case x if x >= 0x20 && x <= 0x10FFFF => true
      case _ => false
    }
  }.const(' ')
  lazy val comment = documentationComment ~|~ lineComment
  lazy val documentationComment = RichTextCodec.literalUnit("///") ~> notNl <~ nl
  lazy val lineComment = RichTextCodec.literalUnit("//") ~>
    (RichTextCodec.filter{ c =>
      c.toInt match {
        case 0x09 => true
        case x if x >= 0x20 && x <= 0x2E => true
        case x if x >= 0x30 && x <= 0x10FFF => true
        case _ => false
      }
    } <~ notNl.repeat.const(Chunk.empty)).optional(' ').const(None) <~ nl
  lazy val comma = RichTextCodec.literalUnit(",")


  def parse(value: String): Smithy = Smithy()

  final case class Resource(
    identifiers: Map[String, ShapeId],
    properties: Map[String, ShapeId],
    create: ShapeId,
    put: ShapeId,
    read: ShapeId,
    update: ShapeId,
    delete: ShapeId,
    list: ShapeId,
    operations: List[ShapeId],
    collectionOperations: List[ShapeId],
    resources: List[ShapeId]
  )

  sealed trait Aggregates
  object Aggregates {
    final case class ListAggregate(member: ShapeId) extends Aggregates

    final case class MapAggregate(key: ShapeId, value: ShapeId) extends Aggregates

    final case class Structure(members: Map[String, ShapeId]) extends Aggregates

    final case class Union(members: List[ShapeId]) extends Aggregates
  }

  final case class ShapeId(id: String)

  final case class Service(
    version: String,
    operations: List[String],
    resources: List[String],
    errors: List[String],
    rename: Map[ShapeId, String]
  )

  final case class Operation(
    name: String,
    mixins: List[String],
    input: String,
    output: String,
    errors: List[String]
  )

  sealed trait SimpleShapes
  object SimpleShapes {
    case object Blob extends SimpleShapes
    case object Boolean extends SimpleShapes
    case object String extends SimpleShapes
    case object Byte extends SimpleShapes
    case object Short extends SimpleShapes
    case object Integer extends SimpleShapes
    case object Long extends SimpleShapes
    case object Float extends SimpleShapes
    case object Double extends SimpleShapes
    case object BigInteger extends SimpleShapes
    case object BigDecimal extends SimpleShapes
    case object Timestamp extends SimpleShapes
    case object Document extends SimpleShapes
    case object List extends SimpleShapes
    case object Map extends SimpleShapes
    case object Structure extends SimpleShapes
    case object Union extends SimpleShapes
    case object Service extends SimpleShapes
    case object Operation extends SimpleShapes
    case object Resource extends SimpleShapes
  }

}

