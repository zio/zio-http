package zio.http.endpoint.openapi

import scala.annotation.tailrec
import zio.Chunk
import zio.http.endpoint.openapi.StringUtils.pascalCase

sealed trait ApiSchemaType extends Product with Serializable {
  def isRef: Boolean    = this.isInstanceOf[ApiSchemaType.Ref]
  def isObject: Boolean = this.isInstanceOf[ApiSchemaType.Object]

  def wrapInOptional(makeOptional: Boolean): ApiSchemaType =
    if (makeOptional && !isOptional) ApiSchemaType.Optional(this) else this

  def unwrapOptional: ApiSchemaType =
    this match {
      case ApiSchemaType.Optional(tpe) => tpe
      case _                           => this
    }

  def isOptional: Boolean = this match {
    case ApiSchemaType.Optional(_) => true
    case _                         => false
  }

  def renderType: String =
    this match {
      case ApiSchemaType.TString          => "String"
      case ApiSchemaType.TBoolean         => "Boolean"
      case ApiSchemaType.TNull            => "Null"
      case ApiSchemaType.TDouble          => "Double"
      case ApiSchemaType.TFloat           => "Float"
      case ApiSchemaType.TInt             => "Int"
      case ApiSchemaType.TLong            => "Long"
      case ApiSchemaType.Optional(tpe)    => s"Option[${tpe.renderType}]"
      case ApiSchemaType.Array(tpe)       => s"Chunk[${tpe.renderType}]"
      case ApiSchemaType.Ref(ref)         => pascalCase(ref.split("/").last)
      case ApiSchemaType.Object(names, _) => names.map(pascalCase).mkString("")
      case ApiSchemaType.AllOf(_, _)      =>
        throw new Exception("AllOf should be eliminated via `normalize` before rendering")
    }

  /**
   * Normalizes this `ApiSchemaType` by substituting all `AllOf` types that
   * contain only objects with single objects comprising all parameters from the
   * inner objects. `ApiSchemaType` instances outside of `AllOf` containers are
   * left unchanged.
   */
  def normalize(schemas: Map[String, ApiSchemaType]): ApiSchemaType =
    this match {
      case ApiSchemaType.AllOf(name, types) =>
        val normalizedTypes =
          types.map(_.normalize(schemas).dereference(schemas))

        if (normalizedTypes.forall(_.isInstanceOf[ApiSchemaType.Object])) {
          val allParams = normalizedTypes
            .flatMap(_.asInstanceOf[ApiSchemaType.Object].params)
            .toMap
          ApiSchemaType.Object(name, allParams)
        } else {
          ApiSchemaType.AllOf(name, normalizedTypes)
        }

      case _ => this
    }

  /**
   * Return all Objects as a Chunk of ApiSchemaType.
   */
  def flattenObjects: Map[String, ApiSchemaType.Object] =
    this match {
      case obj @ ApiSchemaType.Object(_, params) =>
        params.values.flatMap(_.flattenObjects).toMap + (obj.typeName -> obj)
      case ApiSchemaType.Array(tpe)              =>
        tpe.flattenObjects
      case ApiSchemaType.Optional(tpe)           =>
        tpe.flattenObjects
      case _                                     => Map.empty
    }

  @tailrec
  private def dereference(schemas: Map[String, ApiSchemaType]): ApiSchemaType =
    this match {
      case ApiSchemaType.Ref(ref) =>
        schemas
          .getOrElse(
            ref.split("/").last,
            throw new Exception(s"Could not find schema $ref"),
          )
          .dereference(schemas)
      case _                      => this
    }

}

object ApiSchemaType {
  final case class Object(name: Chunk[String], params: Map[String, ApiSchemaType]) extends ApiSchemaType {
    def typeName: String = name.map(pascalCase).mkString("")
  }
  final case class Optional(tpe: ApiSchemaType)                                    extends ApiSchemaType
  final case class Array(tpe: ApiSchemaType)                                       extends ApiSchemaType
  final case class Ref($ref: String)                                               extends ApiSchemaType

  final case class AllOf(name: Chunk[String], types: Chunk[ApiSchemaType]) extends ApiSchemaType

  case object TString  extends ApiSchemaType
  case object TBoolean extends ApiSchemaType
  case object TNull    extends ApiSchemaType
  case object TDouble  extends ApiSchemaType
  case object TFloat   extends ApiSchemaType
  case object TInt     extends ApiSchemaType
  case object TLong    extends ApiSchemaType
}
