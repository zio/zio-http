package zio.http.endpoint.openapi

import scala.collection.immutable.ListMap

import zio.json.ast.Json
import zio.test._

import zio.schema.Schema

import zio.http.endpoint.openapi.JsonSchema.SchemaStyle
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
                       |      "apiKeyAuth" : []
                       |    }
                       |  ]
                       |}""".stripMargin

      assertTrue(toJsonAst(json) == toJsonAst(expected))
    },
    test("JsonSchema.fromZSchemaMulti correctly handles Map schema with List as Value") {
      val schema           = Schema.map[String, List[String]]
      val sch: JsonSchemas = JsonSchema.fromZSchemaMulti(schema, SchemaStyle.Reference)

      val isSchemaProperlyGenerated = if (sch.root.isCollection) sch.root match {
        case JsonSchema.Object(_, additionalProperties, _) =>
          additionalProperties match {
            case Right(JsonSchema.ArrayType(items, _, _)) => items.exists(_.isInstanceOf[JsonSchema.String])
            case _                                        => false
          }
        case _                                             => false
      }
      else false
      assertTrue(isSchemaProperlyGenerated)
    },
  )
}
