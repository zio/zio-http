package zio.http.endpoint.openapi

import scala.collection.immutable.ListMap

import zio.json.ast.Json
import zio.test._

import zio.http.endpoint.openapi.OpenAPI.SecurityScheme._

object OpenAPISpec extends ZIOSpecDefault {

  def toJsonAst(str: String): Json =
    Json.decoder.decodeJson(str).toOption.get

  def toJsonAst(api: OpenAPI): Json =
    toJsonAst(api.toJson)

  val spec = suite("OpenAPISpec")(
    test("auth schema serialization") {
      import OpenAPI._
      val securitySchemes: ListMap[Key, ReferenceOr[SecurityScheme]] = ListMap(
        Key.fromString("apiKeyAuth").get -> ReferenceOr.Or(
          SecurityScheme.ApiKey(
            None,
            "Authorization",
            ApiKey.In.Header,
          ),
        ),
      )

      val openApi  = OpenAPI.empty.copy(
        security = List(SecurityRequirement(Map("apiKeyAuth" -> List.empty))),
        components = Some(OpenAPI.Components(securitySchemes = securitySchemes)),
      )
      val json     = openApi.toJsonPretty
      val expected = """{
                       |  "openapi" : "3.1.0",
                       |  "info" : {
                       |    "title" : "",
                       |    "version" : ""
                       |  },
                       |  "components" : {
                       |    "securitySchemes" : {
                       |      "apiKeyAuth" :
                       |        {
                       |        "type" : "apiKey",
                       |        "name" : "Authorization",
                       |        "in" : "Header"
                       |      }
                       |    }
                       |  },
                       |  "security" : [
                       |    {
                       |      "securitySchemes" : {
                       |        "apiKeyAuth" : []
                       |      }
                       |    }
                       |  ]
                       |}""".stripMargin

      assertTrue(toJsonAst(json) == toJsonAst(expected))
    },
  )
}
