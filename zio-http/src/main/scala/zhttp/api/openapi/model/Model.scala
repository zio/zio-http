package zhttp.api.openapi.model

import zio.json._
import zio.json.ast.Json

import scala.annotation.tailrec

final case class Paths(
  pathObjects: List[PathObject],
) {
  def joinPaths: Paths = {
    @tailrec
    def loop(remaining: List[PathObject], acc: Map[ApiPath, PathObject]): List[PathObject] =
      remaining match {
        case head :: tail =>
          acc.get(head.path) match {
            case Some(value) =>
              loop(tail, acc.updated(head.path, value.copy(operations = value.operations ++ head.operations)))
            case None        => loop(tail, acc + (head.path -> head))
          }
        case Nil          => acc.values.toList
      }

    Paths(loop(pathObjects, Map.empty))
  }

}

object Paths {
  implicit val encoder: JsonEncoder[Paths] =
    JsonEncoder[Json].contramap { (paths: Paths) =>
      val pieces = paths.pathObjects.map { path =>
        path.path.render -> path.operations.toJsonAST.toOption.get
      }
      Json.Obj(pieces: _*)
    }
}

final case class PathObject(
  path: ApiPath,
  operations: Map[String, OperationObject],
)

object PathObject {
  implicit val pathJsonEncoder: JsonEncoder[PathObject] =
    JsonEncoder[zio.json.ast.Json].contramap { (path: PathObject) =>
      Json.Obj(
        path.path.render -> path.operations.toJsonAST.toOption.get,
      )
    }
}

final case class ApiPath(components: List[PathComponent]) {
  def render: String =
    components.map(_.render).mkString("/", "/", "")
}

sealed trait PathComponent extends Product with Serializable {
  def render: String = this match {
    case PathComponent.Literal(string)  => string
    case PathComponent.Variable(string) => s"{$string}"
  }
}

object PathComponent {
  final case class Literal(string: String) extends PathComponent

  final case class Variable(string: String) extends PathComponent
}

final case class OperationObject(
  summary: Option[String],
  description: Option[String],
  parameters: List[ParameterObject],
  requestBody: Option[RequestBodyObject],
  responses: Map[String, ResponseObject],
)

object OperationObject {
  implicit val operationObjectJsonEncoder: JsonEncoder[OperationObject] =
    DeriveJsonEncoder.gen[OperationObject]
}

final case class RequestBodyObject(
  description: String,
  content: Map[String, MediaTypeObject],
  required: Boolean,
)

object RequestBodyObject {
  implicit val requestObjectEncoder: JsonEncoder[RequestBodyObject] =
    DeriveJsonEncoder.gen[RequestBodyObject]
}

final case class ResponseObject(
  description: String,
  //    headers: Map[String, HeaderObject],
  content: Map[String, MediaTypeObject],
)

object ResponseObject {
  implicit val responseObjectJsonEncoder: JsonEncoder[ResponseObject] =
    DeriveJsonEncoder.gen
}

final case class MediaTypeObject(
  schema: SchemaObject,
)

object MediaTypeObject {
  implicit val mediaTypeObjectJsonEncoder: JsonEncoder[MediaTypeObject] =
    DeriveJsonEncoder.gen
}

final case class ParameterObject(
  name: String,
  in: ParameterLocation,
  description: Option[String] = None,
  required: Boolean = false,
  deprecated: Boolean = false,
  schema: SchemaObject,
  //    allowEmptyValue: Boolean = false
)

object ParameterObject {
  implicit val encoder: JsonEncoder[ParameterObject] =
    DeriveJsonEncoder.gen[ParameterObject]
}

sealed trait ParameterLocation extends Product with Serializable

object ParameterLocation {
  case object Query extends ParameterLocation

  case object Header extends ParameterLocation

  case object Path extends ParameterLocation

  case object Cookie extends ParameterLocation

  implicit val encoder: JsonEncoder[ParameterLocation] =
    JsonEncoder.string.contramap {
      case ParameterLocation.Query  => "query"
      case ParameterLocation.Header => "header"
      case ParameterLocation.Path   => "path"
      case ParameterLocation.Cookie => "cookie"
    }
}
