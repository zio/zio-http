package zhttp.api.openapi

import zhttp.api.openapi.model._
import zio.schema._
import zhttp.api._
import zhttp.api.Route._
import zio.json.EncoderOps

import java.util.UUID

object OpenApiInterpreter {

  def getPath(requestParser: RequestParser[_]): ApiPath =
    ApiPath(getPathComponents(requestParser.getRoute))

  def getRouteImpl(requestParser: RequestParser[_]): Option[Route[_]] =
    requestParser match {
      case RequestParser.ZipWith(left, right, _, _) =>
        getRouteImpl(left) orElse getRouteImpl(right)
      case RequestParser.Map(info, _, _)            =>
        getRouteImpl(info)
      case _: Header[_]                             =>
        None
      case _: Query[_]                              =>
        None
      case route: Route[_]                          =>
        Some(route)
    }

  def getRightmostLiteral(route: Route[_]): Option[String] =
    route match {
      case Route.Literal(literal)           =>
        Some(literal)
      case Route.ZipWith(left, right, _, _) =>
        getRightmostLiteral(right) orElse getRightmostLiteral(left)
      case Route.MapRoute(info, _, _)       =>
        getRightmostLiteral(info)
      case _                                => None
    }

  def getPathComponents(route: Route[_], name: Option[String] = None): List[PathComponent] =
    route match {
      case Route.Literal(string)            =>
        List(PathComponent.Literal(string))
      case Route.Match(tpeName, _, _)       =>
        List(PathComponent.Variable(name.map(_ + "Id").getOrElse(tpeName)))
      case Route.ZipWith(left, right, _, _) =>
        getPathComponents(left, name) ++ getPathComponents(right, getRightmostLiteral(left))
      case Route.MapRoute(route, _, _)      =>
        getPathComponents(route, name)
    }

  def pathToParameterObjects(route: Route[_], name: Option[String] = None): List[ParameterObject] =
    route match {
      case Literal(_)                  => List.empty
      case Match(matchName, _, schema) =>
        List(
          ParameterObject(
            name = name.map(_ + "Id").getOrElse(matchName),
            in = ParameterLocation.Path,
            required = true,
            schema = SchemaObject.fromSchema(schema),
          ),
        )
      case ZipWith(left, right, _, _)  =>
        pathToParameterObjects(left, name) ++ pathToParameterObjects(right, getRightmostLiteral(left))
      case MapRoute(route, _, _)       =>
        pathToParameterObjects(route, name)
    }

  def queryParamsToParameterObjects(queryParams: Query[_], optional: Boolean = false): List[ParameterObject] =
    queryParams match {
      case Query.SingleParam(name, _, schema) =>
        List(
          ParameterObject(
            name = name,
            in = ParameterLocation.Query,
            required = !optional,
            schema = SchemaObject.fromSchema(schema),
          ),
        )
      case Query.ZipWith(left, right, _, _)   =>
        queryParamsToParameterObjects(left, optional) ++ queryParamsToParameterObjects(right, optional)
      case Query.MapParams(params, _, _)      =>
        queryParamsToParameterObjects(params, optional)
      case Query.Optional(params)             =>
        queryParamsToParameterObjects(params, true)
    }

  def pathToRequestBodyObject(api: API[_, _, _]): Option[RequestBodyObject] =
    Option.when(api.inputSchema != Schema[Unit]) {
      RequestBodyObject(
        "Input",
        Map(
          "application/json" -> MediaTypeObject(SchemaObject.fromSchema(api.inputSchema)),
        ),
        true,
      )
    }

  def apiToOperation(api: API[_, _, _]): Map[String, OperationObject] =
    Map(
      api.method.toString.toLowerCase ->
        OperationObject(
          None,
          None,
          pathToParameterObjects(api.requestParser.getRoute) ++
            api.requestParser.getQueryParams.toList.flatMap(queryParamsToParameterObjects(_)),
          // TODO: Flesh this out
          pathToRequestBodyObject(api),
          Map(
            "200" -> ResponseObject(
              "OK",
              if (api.outputSchema == Schema[Unit]) Map.empty
              else Map("application/json" -> MediaTypeObject(SchemaObject.fromSchema(api.outputSchema))),
            ),
          ),
        ),
    )

  def apiToPaths(apis: List[API[_, _, _]]): Paths =
    Paths(
      apis.map(api =>
        PathObject(
          path = getPath(api.requestParser),
          operations = apiToOperation(api),
        ),
      ),
    ).joinPaths

  val exampleApi =
    API
      .get("users" / int / "posts" / uuid)
      .query(string("name").?)
      .output[Option[User]]

  val exampleApi2 =
    API
      .post("users")
      .output[List[User]]

  def main(args: Array[String]): Unit = {
    val apis = List(exampleApi, exampleApi2)
    println(apiToPaths(apis).toJsonPretty)
  }

  def generate(apis: APIs)(title: String, description: String): String = {
    val paths   = apiToPaths(apis.toList)
    val openApi = OpenApi("3.0.0", Info("1.0.0", title, description), paths)
    openApi.toJson
  }

}

final case class User(id: UUID, email: String, score: Int)

object User {
  implicit lazy val schema: Schema[User] = DeriveSchema.gen[User]
}
