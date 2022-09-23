package zio.http.api.openapi

import zio.http.api.Doc
import zio.http.api.openapi.JsonRenderer._
import zio.http.api.openapi.OpenAPI.Parameter.{Definition, QueryParameter}
import zio.http.api.openapi.OpenAPI.Schema.ResponseSchema
import zio.http.api.openapi.OpenAPI.SecurityScheme.ApiKey
import zio.http.api.openapi.OpenAPI.{Info, Operation, PathItem}
import zio.http.model.Status
import zio.test._

import java.net.URI
import scala.util.Try

object JsonRendererSpec extends ZIOSpecDefault {
  case object Html
  override def spec =
    suite("JsonRenderer")(
      test("render numbers") {
        val rendered =
          JsonRenderer.renderFields("int" -> 1, "double" -> 1.0d, "float" -> 1.0f, "long" -> 1L)
        val expected = """{"int":1,"double":1.0,"float":1.0,"long":1}"""
        assertTrue(rendered == expected)
      },
      test("render strings") {
        val rendered = JsonRenderer.renderFields("string" -> "string")
        val expected = """{"string":"string"}"""
        assertTrue(rendered == expected)
      },
      test("render booleans") {
        val rendered = JsonRenderer.renderFields("boolean" -> true)
        val expected = """{"boolean":true}"""
        assertTrue(rendered == expected)
      },
      test("render tuples") {
        val rendered = JsonRenderer.renderFields(("tuple", (1, "string")))
        val expected = """{"tuple":{"1":"string"}}"""
        assertTrue(rendered == expected)
      },
      test("render list") {
        val rendered = JsonRenderer.renderFields("array" -> List(1, 2, 3))
        val expected = """{"array":[1,2,3]}"""
        assertTrue(rendered == expected)
      },
      test("render map") {
        val rendered =
          JsonRenderer.renderFields("map" -> Map("key" -> "value"), "otherMap" -> Map(1 -> "value"))
        val expected = """{"map":{"key":"value"},"otherMap":{"1":"value"}}"""
        assertTrue(rendered == expected)
      },
      test("render In") {
        val rendered = JsonRenderer.renderFields("type" -> ApiKey.In.Query)
        val expected = """{"type":"query"}"""
        assertTrue(rendered == expected)
      },
      test("render empty doc") {
        val rendered = JsonRenderer.renderFields("doc" -> Doc.empty)
        val expected = """{"doc":""}"""
        assertTrue(rendered == expected)
      },
      test("render doc") {
        val rendered = JsonRenderer.renderFields("doc" -> Doc.p(Doc.Span.uri(new URI("https://google.com"))))
        val expected = """{"doc":"<p><a href="https://google.com">https://google.com</a></p>"}"""
        assertTrue(rendered == expected)
      },
      test("throw exception for duplicate keys") {
        val rendered = Try(JsonRenderer.renderFields("key" -> 1, "key" -> 2))
        assertTrue(rendered.failed.toOption.exists(_.isInstanceOf[IllegalArgumentException]))
      },
      test("render OpenAPI") {
        val rendered =
          OpenAPI
            .OpenAPI(
              info = Info(
                title = "title",
                version = "version",
                description = Doc.p("description"),
                termsOfService = new URI("https://google.com"),
                contact = None,
                license = None,
              ),
              servers = List(OpenAPI.Server(new URI("https://google.com"), Doc.p("description"), Map.empty)),
              paths = Map(
                OpenAPI.Path.fromString("/test").get -> PathItem(
                  get = Some(
                    Operation(
                      responses = Map(
                        Status.Ok -> OpenAPI.Response(
                          description = Doc.p(Doc.Span.text("description")),
                          content = Map(
                            "application/json" -> OpenAPI.MediaType(
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
                      externalDocs = Some(OpenAPI.ExternalDoc(None, new URI("https://google.com"))),
                      operationId = Some("operationId"),
                      parameters = Set(
                        QueryParameter(
                          "name",
                          Doc.p("description"),
                          definition = Definition.Content("key", "mediaType"),
                          examples = Map.empty,
                        ),
                      ),
                      servers = List(OpenAPI.Server(new URI("https://google.com"), Doc.p("description"), Map.empty)),
                      requestBody = None,
                      callbacks = Map.empty,
                      security = List.empty,
                    ),
                  ),
                  ref = "ref",
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
                OpenAPI.Components(
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
              externalDocs = Some(OpenAPI.ExternalDoc(None, new URI("https://google.com"))),
              openapi = "3.0.0",
            )
            .toJson

        val expected =
          """{"openapi":"3.0.0","info":{"title":"title","description":"<p>description</p>","termsOfService":"https://google.com","version":"version"},"servers":[{"url":"https://google.com","description":"<p>description</p>","variables":{}}],"paths":{"/test":{"$ref":"ref","summary":"","description":"<p>description</p>","get":{"tags":["tag"],"summary":"summary","description":"<p>description</p>","externalDocs":{"url":"https://google.com"},"operationId":"operationId","parameters":[{"name":"name","in":"query","description":"<p>description</p>","required":true,"deprecated":false,"allowEmptyValue":false,"definition":{"key":"key","mediaType":"mediaType"},"explode":true,"examples":{}}],"responses":{"200":{"description":"<p>description</p>","headers":{},"content":{"application/json":{"schema":{"nullable":false,"readOnly":true,"writeOnly":false,"externalDocs":"https://google.com","example":"Example","deprecated":false},"examples":{},"encoding":{}}},"links":{}}},"callbacks":{},"deprecated":false,"security":[],"servers":[{"url":"https://google.com","description":"<p>description</p>","variables":{}}]},"servers":[],"parameters":[]}},"components":{"schemas":{},"responses":{},"parameters":{},"examples":{},"requestBodies":{},"headers":{},"securitySchemes":{},"links":{},"callbacks":{}},"security":[],"tags":[],"externalDocs":{"url":"https://google.com"}}"""
        assertTrue(rendered == expected)
      },
    )
}
