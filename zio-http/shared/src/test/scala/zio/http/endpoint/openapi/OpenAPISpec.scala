package zio.http.endpoint.openapi

import java.util.UUID

import scala.collection.immutable.ListMap

import zio.json.ast.Json
import zio.test._

import zio.schema.annotation.discriminatorName
import zio.schema.{DeriveSchema, Schema}

import zio.http.endpoint.openapi.JsonSchema.{SchemaRef, SchemaStyle}
import zio.http.endpoint.openapi.OpenAPI.ReferenceOr
import zio.http.endpoint.openapi.OpenAPI.SecurityScheme._

object OpenAPISpec extends ZIOSpecDefault {

  def toJsonAst(str: String): Json =
    Json.decoder.decodeJson(str).toOption.get

  def toJsonAst(api: OpenAPI): Json =
    toJsonAst(api.toJson)

  @discriminatorName("type") sealed trait SealedTrait
  object SealedTrait {
    case class One(set: Set[String])   extends SealedTrait
    case class Two(list: List[String]) extends SealedTrait
  }

  case class SchemaTest(number: Int, string: String, child: Option[SealedTrait])
  implicit val schemaTestSchema: Schema[SchemaTest] = DeriveSchema.gen[SchemaTest]

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
                       |        "in" : "header"
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
      val sch: JsonSchemas = JsonSchema.fromZSchemaMultiple(schema, SchemaRef.openApi(SchemaStyle.Reference))

      val isSchemaProperlyGenerated = if (sch.root.isCollection) sch.root match {
        case JsonSchema.Object(_, additionalProperties, _) =>
          additionalProperties match {
            case Right(JsonSchema.ArrayType(items, _, _)) =>
              items.exists(_.isInstanceOf[JsonSchema.String])
            case _                                        => false
          }
        case _                                             => false
      }
      else false
      assertTrue(isSchemaProperlyGenerated)
    },
    test("JsonSchema.fromZSchema correctly handles Map with non-simple string keys") {
      val schema   = Schema.map[UUID, String]
      val js       = JsonSchema.fromZSchema(schema, SchemaRef.openApi(SchemaStyle.Inline))
      val oapi     = OpenAPI.empty.copy(
        components =
          Some(OpenAPI.Components(schemas = ListMap(OpenAPI.Key.fromString("IdToName").get -> ReferenceOr.Or(js)))),
      )
      val json     = oapi.toJsonPretty
      val expected = """{
                       |  "openapi" : "3.1.0",
                       |  "info" : {
                       |    "title" : "",
                       |    "version" : ""
                       |  },
                       |  "components" : {
                       |    "schemas" : {
                       |      "IdToName" :
                       |        {
                       |        "type" :
                       |          "object",
                       |        "properties" : {},
                       |        "additionalProperties" :
                       |          {
                       |          "type" :
                       |            "string",
                       |          "x-string-key-schema" : {
                       |            "type" :
                       |              "string",
                       |            "format" : "uuid"
                       |          }
                       |        }
                       |      }
                       |    }
                       |  }
                       |}""".stripMargin
      assertTrue(toJsonAst(json) == toJsonAst(expected))
    },
    test("JsonSchema.jsonSchema correctly generate valid Json Schema with $defs and associated $ref") {
      val jsonSchema = JsonSchema.jsonSchema(schemaTestSchema)
      val json       = jsonSchema.toJsonPretty
      val expected   = f"""{"$$schema" : "https://json-schema.org/draft/2020-12/schema",""" +
        """  "type" : "object",
          |  "properties" : {
          |    "number" : {
          |      "type" : "integer",
          |      "format" : "int32"
          |    },
          |    "string" : {
          |      "type" : "string"
          |    },
          |    "child" : {
          |      "anyOf" : [
          |        {
          |          "type" : "null"
          |        },
          |        {
          |          "$ref" : "#/$defs/SealedTrait"
          |        }
          |      ]
          |    }
          |  },
          |  "required" : [
          |    "number",
          |    "string"
          |  ],
          |  "$defs" : {
          |    "One" : {
          |      "type" : "object",
          |      "properties" : {
          |        "set" : {
          |          "type" : "array",
          |          "items" : {
          |            "type" : "string"
          |          },
          |          "uniqueItems" : true
          |        }
          |      },
          |      "required" : [
          |        "set"
          |      ]
          |    },
          |    "Two" : {
          |      "type" : "object",
          |      "properties" : {
          |        "list" : {
          |          "type" : "array",
          |          "items" : {
          |            "type" : "string"
          |          }
          |        }
          |      },
          |      "required" : [
          |        "list"
          |      ]
          |    },
          |    "SealedTrait" : {
          |      "oneOf" : [
          |        {
          |          "$ref" : "#/$defs/One"
          |        },
          |        {
          |          "$ref" : "#/$defs/Two"
          |        }
          |      ],
          |      "discriminator" : {
          |        "propertyName" : "type",
          |        "mapping" : {
          |          "One" : "#/$defs/One",
          |          "Two" : "#/$defs/Two"
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin

      assertTrue(toJsonAst(json) == toJsonAst(expected))
    },
  )
}
