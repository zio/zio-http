package zio.http.api.openapi

import zio.http.api.Doc
import zio.http.api.openapi.OpenAPI.Parameter.{Definition, QueryParameter}
import zio.http.api.openapi.OpenAPI.Schema.ResponseSchema
import zio.http.api.openapi.OpenAPI._
import zio.http.model.Status
import zio.test._

import java.net.URI
import scala.util.Try

object JsonRendererSpec extends ZIOSpecDefault {
  final case class Person(name: String, age: Int)
  case object Html
  override def spec =
    suite("JsonRenderer")(
      test("render numbers") {
        val rendered =
          JsonRenderer.renderFields("int" -> 1 :: "double" -> 1.0d :: "float" -> 1.0f :: "long" -> 1L :: Nil)
        val expected = """{"int":1,"double":1.0,"float":1.0,"long":1}"""
        assertTrue(rendered == expected)
      },
      test("render strings") {
        val rendered = JsonRenderer.renderFields("string" -> "string" :: Nil)
        val expected = """{"string":"string"}"""
        assertTrue(rendered == expected)
      },
      test("render booleans") {
        val rendered = JsonRenderer.renderFields("boolean" -> true :: Nil)
        val expected = """{"boolean":true}"""
        assertTrue(rendered == expected)
      },
      test("render tuples") {
        val rendered = JsonRenderer.renderFields("tuple" -> (1, "string") :: Nil)
        val expected = """{"tuple":{"1":"string"}}"""
        assertTrue(rendered == expected)
      },
      test("render list") {
        val rendered = JsonRenderer.renderFields("array" -> List(1, 2, 3) :: Nil)
        val expected = """{"array":[1,2,3]}"""
        assertTrue(rendered == expected)
      },
      test("render map") {
        val rendered =
          JsonRenderer.renderFields("map" -> Map("key" -> "value") :: "otherMap" -> Map(1 -> "value") :: Nil)
        val expected = """{"map":{"key":"value"},"otherMap":{"1":"value"}}"""
        assertTrue(rendered == expected)
      },
      test("render product as field value") {
        val rendered = JsonRenderer.renderFields("person" -> Person("John", 42) :: Nil)
        val expected = """{"person":{"name":"John","age":42}}"""
        assertTrue(rendered == expected)
      },
      test("render product as field value in list") {
        val rendered = JsonRenderer.renderFields("people" -> List(Person("John", 42), Person("Jane", 42)) :: Nil)
        val expected = """{"people":[{"name":"John","age":42},{"name":"Jane","age":42}]}"""
        assertTrue(rendered == expected)
      },
      test("render product as field value in map") {
        val rendered = JsonRenderer.renderFields("people" -> Map("John" -> Person("John", 42)) :: Nil)
        val expected = """{"people":{"John":{"name":"John","age":42}}}"""
        assertTrue(rendered == expected)
      },
      test("render product as field value in map in list") {
        val rendered = JsonRenderer.renderFields("people" -> List(Map("John" -> Person("John", 42))) :: Nil)
        val expected = """{"people":[{"John":{"name":"John","age":42}}]}"""
        assertTrue(rendered == expected)
      },
      test("render product as field value in map in map") {
        val rendered = JsonRenderer.renderFields("people" -> Map("John" -> Map("John" -> Person("John", 42))) :: Nil)
        val expected = """{"people":{"John":{"John":{"name":"John","age":42}}}}"""
        assertTrue(rendered == expected)
      },
      test("render product") {
        val rendered = JsonRenderer.renderProduct(Person("John", 42))
        val expected = """{"name":"John","age":42}"""
        assertTrue(rendered == expected)
      },
      test("render singleton") {
        val rendered = JsonRenderer.renderFields("type" -> Html :: Nil)
        val expected = """{"type":"html"}"""
        assertTrue(rendered == expected)
      },
      test("render empty doc") {
        val rendered = JsonRenderer.renderFields("doc" -> Doc.empty :: Nil)
        val expected = """{"doc":""}"""
        assertTrue(rendered == expected)
      },
      test("render doc") {
        val rendered = JsonRenderer.renderFields("doc" -> Doc.p(Doc.Span.uri(new URI("https://google.com"))) :: Nil)
        val expected = """{"doc":"https://google.com<br/>"}"""
        assertTrue(rendered == expected)
      },
      test("throw exception for duplicate keys") {
        val rendered = Try(JsonRenderer.renderFields("key" -> 1 :: "key" -> 2 :: Nil))
        assertTrue(rendered.failed.toOption.exists(_.isInstanceOf[IllegalArgumentException]))
      },
      test("render OpenAPI") {
        val rendered = JsonRenderer
          .renderProduct(
            OpenAPI(
              info = Info(
                title = "title",
                version = "version",
                description = Doc.p("description"),
                termsOfService = new URI("https://google.com"),
                contact = None,
                license = None,
              ),
              servers = List(Server(new URI("https://google.com"), Doc.p("description"), Map.empty)),
              paths = Map(
                Path.fromString("/test").get -> PathItem(
                  get = Some(
                    Operation(
                      responses = Map(
                        Status.Ok -> Response(
                          description = Doc.p(Doc.Span.text("description")),
                          content = Map(
                            "application/json" -> MediaType(
                              schema = ResponseSchema(
                                discriminator = None,
                                xml = None,
                                externalDocs = new URI("https://google.com"),
                                example = "Example",
                              ),
                              examples = Map.empty,
                              encoding = Map.empty,
                            ),
                          ),
                          headers = Map.empty,
                          links = Map.empty,
                        ),
                      ),
                      tags = List("tag"),
                      summary = "summary",
                      description = Doc.p("description"),
                      externalDocs = Some(ExternalDoc(None, new URI("https://google.com"))),
                      operationId = Some("operationId"),
                      parameters = Set(
                        QueryParameter(
                          "name",
                          Doc.p("description"),
                          definition = Definition.Content("key", "mediaType"),
                          examples = Map.empty,
                        ),
                      ),
                      servers = List(Server(new URI("https://google.com"), Doc.p("description"), Map.empty)),
                      requestBody = None,
                      callbacks = Map.empty,
                      security = List.empty,
                    ),
                  ),
                  `$ref` = "ref",
                  description = Doc.p("description"),
                  put = None,
                  post = None,
                  delete = None,
                  options = None,
                  head = None,
                  patch = None,
                  trace = None,
                  servers = List.empty,
                  parameters = Set.empty,
                ),
              ),
              components = Some(
                Components(
                  schemas = Map.empty,
                  responses = Map.empty,
                  parameters = Map.empty,
                  examples = Map.empty,
                  requestBodies = Map.empty,
                  headers = Map.empty,
                  securitySchemes = Map.empty,
                  links = Map.empty,
                  callbacks = Map.empty,
                ),
              ),
              security = List.empty,
              tags = List.empty,
              externalDocs = Some(ExternalDoc(None, new URI("https://google.com"))),
              openapi = "3.0.0",
            ),
          )
        val expected =
          """{"openapi":"3.0.0","info":{"title":"title","description":"description<br/>","termsOfService":"https://google.com","version":"version"},"servers":[{"url":"https://google.com","description":"description<br/>","variables":{}}],"paths":{"/test":{"$ref":"ref","summary":"","description":"description<br/>","get":{"value":{"tags":["tag"],"summary":"summary","description":"description<br/>","externalDocs":{"value":{"url":"https://google.com"}},"operationId":{"value":"operationId"},"parameters":[{"name":"name","in":"query","description":"description<br/>","required":true,"deprecated":false,"allowEmptyValue":false,"definition":{"key":"key","mediaType":"mediaType"},"explode":true,"examples":{}}],"responses":{"200":{"description":"description<br/>","headers":{},"content":{"application/json":{"schema":{"nullable":false,"readOnly":true,"writeOnly":false,"externalDocs":"https://google.com","example":"Example","deprecated":false},"examples":{},"encoding":{}}},"links":{}}},"callbacks":{},"deprecated":false,"security":[],"servers":[{"url":"https://google.com","description":"description<br/>","variables":{}}]}},"servers":[],"parameters":[]}},"components":{"value":{"schemas":{},"responses":{},"parameters":{},"examples":{},"requestBodies":{},"headers":{},"securitySchemes":{},"links":{},"callbacks":{}}},"security":[],"tags":[],"externalDocs":{"value":{"url":"https://google.com"}}}"""
        assertTrue(rendered == expected)
      },
    )
}
