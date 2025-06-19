package zio.http.endpoint.openapi

import java.util.UUID

import scala.collection.immutable.ListMap

import zio.NonEmptyChunk
import zio.json.ast.Json
import zio.test._

import zio.schema.{DeriveSchema, Schema}

import zio.http.endpoint.openapi.JsonSchema.SchemaStyle
import zio.http.endpoint.openapi.OpenAPI.ReferenceOr

object JsonSchemaSpec extends ZIOSpecDefault {

  final case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = DeriveSchema.gen[Address]
  }
  final case class Person(name: String, address: Address)
  object Person  {
    implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
  }

  final case class NodeA(b: NodeB)
  object NodeA {
    implicit lazy val schema: Schema[NodeA] = DeriveSchema.gen[NodeA]
  }

  final case class NodeB(a: NodeA)
  object NodeB {
    implicit lazy val schema: Schema[NodeB] = DeriveSchema.gen[NodeB]
  }

  final case class SelfRec(next: SelfRec)
  object SelfRec {
    implicit lazy val schema: Schema[SelfRec] = DeriveSchema.gen[SelfRec]
  }

  private def toJsonAst(str: String): Json =
    Json.decoder.decodeJson(str).toOption.get

  val spec = suite("JsonSchemaSpec")(
    suite("fromZSchema")(
      test("correctly handles Map with non-simple string keys") {
        val schema = Schema.map[UUID, String]
        val js     = JsonSchema.fromZSchema(schema)
        val oapi   = OpenAPI.empty.copy(
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
        assert(toJsonAst(json))(Assertion.equalTo(toJsonAst(expected)))
      },
      test("SchemaStyle.Compact returns exact $ref JSON path with compact name") {
        val jsonSchema = JsonSchema.fromZSchema(implicitly[Schema[Person]], SchemaStyle.Compact)

        val expectedJson = toJsonAst("""{ "$ref": "#/components/schemas/Person" }""")
        assert(toJsonAst(jsonSchema.toJson))(Assertion.equalTo(expectedJson))
      },
      test("SchemaStyle.Reference returns $ref-only JSON for Person") {
        val js = JsonSchema.fromZSchema(implicitly[Schema[Person]], SchemaStyle.Reference)

        val expectedJson = toJsonAst(
          """{ "$ref": "#/components/schemas/zio_http_endpoint_openapi_JsonSchemaSpec_Person" }""",
        )

        assert(toJsonAst(js.toJson))(Assertion.equalTo(expectedJson))
      },
      test("SchemaStyle.Compact returns $ref-only JSON for Person") {
        val js = JsonSchema.fromZSchema(implicitly[Schema[Person]], SchemaStyle.Compact)

        val expectedJson = toJsonAst("""{ "$ref": "#/components/schemas/Person" }""")

        assert(toJsonAst(js.toJson))(Assertion.equalTo(expectedJson))
      },
    ),
    suite("fromZSchemaMulti")(
      test("correctly handles Map schema with List as Value") {
        val schema = Schema.map[String, List[String]]
        val sch    = JsonSchema.fromZSchemaMulti(schema, SchemaStyle.Reference)

        val hasStringArrayValues: Assertion[JsonSchema] =
          Assertion.assertion("hasStringArrayValues") {
            case JsonSchema.Object(_, Right(JsonSchema.ArrayType(items, _, _)), _) =>
              items.exists(_.isInstanceOf[JsonSchema.String])
            case _                                                                 => false
          }

        assert(sch.root)(hasStringArrayValues)
      },
      test("collects child schemas of nested records (Reference)") {
        val jsMulti = JsonSchema.fromZSchemaMulti(implicitly[Schema[Person]], SchemaStyle.Reference)

        val personRef  = "#/components/schemas/zio_http_endpoint_openapi_JsonSchemaSpec_Person"
        val addressRef = "#/components/schemas/zio_http_endpoint_openapi_JsonSchemaSpec_Address"

        assertTrue(
          jsMulti.rootRef.contains(personRef) &&
            jsMulti.children.contains(addressRef) &&
            jsMulti.children(addressRef).isInstanceOf[JsonSchema.Object],
        )
      },
      test("collects child schemas with compact refs (Compact)") {
        val jsMulti = JsonSchema.fromZSchemaMulti(implicitly[Schema[Person]], SchemaStyle.Compact)

        val personRef  = "#/components/schemas/Person"
        val addressRef = "#/components/schemas/Address"

        assertTrue(
          jsMulti.rootRef.contains(personRef) &&
            jsMulti.children.contains(addressRef) &&
            jsMulti.children(addressRef).isInstanceOf[JsonSchema.Object],
        )
      },
    ),
    suite("fromZSchemaInlineDeepOrFail")(
      test("inlines non-recursive schema") {
        val result = JsonSchema.fromZSchemaInlineDeepOrFail(implicitly[Schema[Person]])

        val expectedJson = toJsonAst(
          """{
            |"type":"object",
            |"properties":{
            |  "name":{"type":"string"},
            |  "address":{
            |    "type":"object",
            |    "properties":{
            |      "street":{"type":"string"},
            |      "city":{"type":"string"}
            |    },
            |    "required":["street","city"]
            |  }
            |},
            |"required":["name","address"]
            |}""".stripMargin,
        )

        assertTrue(result.isRight) &&
        assert(toJsonAst(result.toOption.get.toJson))(Assertion.equalTo(expectedJson))
      },
      test("detects simple recursion") {
        val res      = JsonSchema.fromZSchemaInlineDeepOrFail(implicitly[Schema[SelfRec]])
        val expected = NonEmptyChunk("#/components/schemas/SelfRec")

        assertTrue(
          res == Left(JsonSchema.GenerationError.RecursionDetected(expected)),
        )
      },
      test("detects mutual recursion") {
        val res      = JsonSchema.fromZSchemaInlineDeepOrFail(implicitly[Schema[NodeA]])
        val expected = NonEmptyChunk("#/components/schemas/NodeA")

        assertTrue(
          res == Left(JsonSchema.GenerationError.RecursionDetected(expected)),
        )
      },
    ),
  )
}
