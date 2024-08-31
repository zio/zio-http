package zio.http.gen.scala

import scala.meta.Term
import scala.meta.prettyprinters.XtensionSyntax

import zio.http.gen.openapi.Config.NormalizeFields
import zio.http.{Method, Status}

import com.sun.tools.javac.code.TypeMetadata.Annotations

sealed trait Code extends Product with Serializable

object Code {
  sealed trait ScalaType extends Code { self =>
    def seq(nonEmpty: Boolean): Collection.Seq = Collection.Seq(self, nonEmpty)
    def set(nonEmpty: Boolean): Collection.Set = Collection.Set(self, nonEmpty)
    def map: Collection.Map                    = Collection.Map(self, None)
    def opt: Collection.Opt                    = Collection.Opt(self)
  }

  object ScalaType {
    case object Inferred                                   extends ScalaType
    case object Unit                                       extends ScalaType
    case object JsonAST                                    extends ScalaType
    final case class Or(left: ScalaType, right: ScalaType) extends ScalaType
  }

  final case class TypeRef(name: String) extends ScalaType

  final case class Files(files: List[File]) extends Code

  final case class File(
    path: List[String],
    pkgPath: List[String],
    imports: List[Import],
    objects: List[Object],
    caseClasses: List[CaseClass],
    enums: List[Enum],
  ) extends Code

  sealed trait Import extends Code

  object Import {
    def apply(name: String): Import = Absolute(name)

    final case class Absolute(path: String) extends Import
    final case class FromBase(path: String) extends Import
  }

  final case class Object(
    name: String,
    extensions: List[String],
    schema: Option[Object.SchemaCode],
    endpoints: Map[Field, EndpointCode],
    objects: List[Object],
    caseClasses: List[CaseClass],
    enums: List[Enum],
  ) extends ScalaType

  object Object {

    /**
     * This is a means to provide implicit codec/schema in different ways. e.g.
     * deriving with macros, or manual transforming on a primitive type.
     */
    sealed trait SchemaCode {
      def codecLineWithStringBuilder(typeName: String, sb: StringBuilder): Unit
    }
    object SchemaCode       {
      case object DeriveSchemaGen                      extends SchemaCode {
        override def codecLineWithStringBuilder(typeName: String, sb: StringBuilder): Unit = {
          sb ++= " implicit val codec: Schema["
          sb ++= typeName
          sb ++= "] = DeriveSchema.gen["
          sb ++= typeName
          sb += ']'
        }
      }
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

    def withDefaultSchemaDerivation(
      name: String,
      extensions: List[String],
      endpoints: Map[Field, EndpointCode],
      objects: List[Object],
      caseClasses: List[CaseClass],
      enums: List[Enum],
    ): Object =
      Object(name, extensions, Some(SchemaCode.DeriveSchemaGen), endpoints, objects, caseClasses, enums)

    def schemaCompanion(str: String): Object = withDefaultSchemaDerivation(
      name = str,
      extensions = Nil,
      endpoints = Map.empty,
      objects = Nil,
      caseClasses = Nil,
      enums = Nil,
    )

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

  final case class CaseClass(name: String, fields: List[Field], companionObject: Option[Object], mixins: List[String])
      extends ScalaType

  object CaseClass {
    def apply(name: String, mixins: List[String]): CaseClass = CaseClass(name, Nil, None, mixins)
  }

  final case class Enum(
    name: String,
    cases: List[CaseClass],
    caseNames: List[String] = Nil,
    discriminator: Option[String] = None,
    noDiscriminator: Boolean = false,
    schema: Boolean = true,
    abstractMembers: List[Field] = Nil,
  ) extends ScalaType

  final case class Annotation(value: String, imports: List[Code.Import]) extends Code

  sealed abstract case class Field private (name: String, fieldType: ScalaType, annotations: List[Annotation])
      extends Code {
    // only allow copy on fieldType, since name is mangled to be valid in smart constructor
    def copy(fieldType: ScalaType = fieldType, annotations: List[Annotation] = annotations): Field =
      new Field(name, fieldType, annotations) {}
  }

  object Field {

    def apply(name: String): Field                                               = apply(name, ScalaType.Inferred)
    def apply(name: String, fieldType: ScalaType): Field                         = {
      val validScalaTermName = Term.Name(name).syntax
      new Field(validScalaTermName, fieldType, Nil) {}
    }
    def apply(name: String, fieldType: ScalaType, annotation: Annotation): Field = {
      val validScalaTermName = Term.Name(name).syntax
      new Field(validScalaTermName, fieldType, List(annotation)) {}
    }
    def apply(name: String, fieldType: ScalaType, annotations: List[Annotation]): Field = {
      val validScalaTermName = Term.Name(name).syntax
      new Field(validScalaTermName, fieldType, annotations) {}
    }
  }

  sealed trait Collection extends ScalaType {
    def elementType: ScalaType
  }

  object Collection {
    final case class Seq(elementType: ScalaType, nonEmpty: Boolean)           extends Collection
    final case class Set(elementType: ScalaType, nonEmpty: Boolean)           extends Collection
    final case class Map(elementType: ScalaType, keysType: Option[ScalaType]) extends Collection
    final case class Opt(elementType: ScalaType)                              extends Collection
  }

  sealed trait Primitive extends ScalaType

  object Primitive {
    case object ScalaInt     extends Primitive
    case object ScalaLong    extends Primitive
    case object ScalaDouble  extends Primitive
    case object ScalaFloat   extends Primitive
    case object ScalaChar    extends Primitive
    case object ScalaByte    extends Primitive
    case object ScalaShort   extends Primitive
    case object ScalaBoolean extends Primitive
    case object ScalaUnit    extends Primitive
    case object ScalaUUID    extends Primitive
    case object ScalaString  extends Primitive
  }

  final case class EndpointCode(
    method: Method,
    pathPatternCode: PathPatternCode,
    queryParamsCode: Set[QueryParamCode],
    headersCode: HeadersCode,
    inCode: InCode,
    outCodes: List[OutCode],
    errorsCode: List[OutCode],
  ) extends Code

  final case class PathPatternCode(segments: List[PathSegmentCode])
  final case class PathSegmentCode(name: String, segmentType: CodecType)
  object PathSegmentCode {
    def apply(name: String): PathSegmentCode = PathSegmentCode(name, CodecType.Literal)
  }
  sealed trait CodecType
  object CodecType       {
    case object Boolean                                            extends CodecType
    case object Int                                                extends CodecType
    case object Literal                                            extends CodecType
    case object Long                                               extends CodecType
    case object String                                             extends CodecType
    case object UUID                                               extends CodecType
    case class Aliased(underlying: CodecType, newtypeName: String) extends CodecType
  }
  final case class QueryParamCode(name: String, queryType: CodecType)
  final case class HeadersCode(headers: List[HeaderCode])
  object HeadersCode     { val empty: HeadersCode = HeadersCode(Nil)                                         }
  final case class HeaderCode(name: String)
  final case class InCode(
    inType: String,
    name: Option[String],
    doc: Option[String],
    streaming: Boolean,
  )
  object InCode          { def apply(inType: String): InCode = InCode(inType, None, None, streaming = false) }
  final case class OutCode(
    outType: String,
    status: Status,
    mediaType: Option[String],
    doc: Option[String],
    streaming: Boolean,
  )
  object OutCode         {
    def apply(outType: String, status: Status): OutCode = OutCode(outType, status, None, None, streaming = false)
    def json(outType: String, status: Status): OutCode  =
      OutCode(outType, status, Some("application/json"), None, streaming = false)
  }

}
