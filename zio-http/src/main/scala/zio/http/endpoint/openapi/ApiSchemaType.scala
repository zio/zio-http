package zio.http.endpoint.openapi

import zio.Chunk

import scala.annotation.tailrec

sealed trait ApiSchemaType extends Product with Serializable {

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
      case ApiSchemaType.TString       => "String"
      case ApiSchemaType.TBoolean      => "Boolean"
      case ApiSchemaType.TNull         => "Null"
      case ApiSchemaType.TDouble       => "Double"
      case ApiSchemaType.TFloat        => "Float"
      case ApiSchemaType.TInt          => "Int"
      case ApiSchemaType.TLong         => "Long"
      case ApiSchemaType.Optional(tpe) => s"Option[${tpe.renderType}]"
      case ApiSchemaType.Array(tpe)    => s"Chunk[${tpe.renderType}]"
      case ApiSchemaType.Ref(ref)      => ref.split("/").last
      case ApiSchemaType.Object(_)     => "Object"
      case ApiSchemaType.AllOf(_)      =>
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
      case ApiSchemaType.AllOf(types) =>
        val normalizedTypes =
          types.map(_.normalize(schemas).dereference(schemas))

        if (normalizedTypes.forall(_.isInstanceOf[ApiSchemaType.Object])) {
          val allParams = normalizedTypes
            .flatMap(_.asInstanceOf[ApiSchemaType.Object].params)
            .toMap
          ApiSchemaType.Object(allParams)
        } else {
          ApiSchemaType.AllOf(normalizedTypes)
        }

      case _ => this
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
  final case class Object(params: Map[String, ApiSchemaType]) extends ApiSchemaType
  final case class Optional(tpe: ApiSchemaType)               extends ApiSchemaType
  final case class Array(tpe: ApiSchemaType)                  extends ApiSchemaType
  final case class Ref($ref: String)                          extends ApiSchemaType

  final case class AllOf(types: Chunk[ApiSchemaType]) extends ApiSchemaType

  case object TString  extends ApiSchemaType
  case object TBoolean extends ApiSchemaType
  case object TNull    extends ApiSchemaType
  case object TDouble  extends ApiSchemaType
  case object TFloat   extends ApiSchemaType
  case object TInt     extends ApiSchemaType
  case object TLong    extends ApiSchemaType
}
