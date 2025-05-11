package zio.http.endpoint.openapi

import zio.test.*
import zio.schema.{ Schema, derived }
import zio.schema.DeriveSchema
import zio.test.*
import zio.http.endpoint.Endpoint
import zio.http.codec.{ Doc, HttpCodec }
import zio.http.endpoint.Endpoint
import zio.http.endpoint.openapi.JsonSchema.StringFormat.Email
import zio.http.endpoint.openapi.JsonSchema.format
import zio.http.endpoint.openapi.OpenAPI.ReferenceOr
import zio.http.endpoint.openapi.OpenAPISpec.{ test, toJsonAst }
import zio.http.{ MediaType, RoutePattern }
import zio.schema.annotation.{ caseName, discriminatorName }
import zio.prelude.Subtype

import scala.collection.immutable.ListMap

object Scala3OpenAPIGenSpec extends ZIOSpecDefault {

  type NonEmptyString = NonEmptyString.Type
  object NonEmptyString extends Subtype[String] {
    inline override def assertion = !zio.prelude.Assertion.isEmptyString

    given zio.schema.Schema[Type] = derive
  }

  @discriminatorName("type")
  sealed trait Input derives Schema
  object Input {
    @caseName("HTTP")
    final case class HttpInput(request: HttpInput.Request) extends Input derives Schema

    object HttpInput {
      enum Method derives Schema {
        case GET, POST, PUT
      }

      final case class Request(method: Method, url: String) derives Schema
    }
  }


  sealed trait Error extends Product with Serializable
  object Error {
    final case class Error0(errors: List[String]) extends Error derives Schema
    final case class Error1(message: String)     extends Error derives Schema
  }

  @discriminatorName("type")
  sealed trait Output derives Schema
  object Output {
    @caseName("HTTP")
    final case class HttpOutput(body: Option[HttpOutput.Body], headers: Map[String, String]) extends Output derives Schema

    object HttpOutput {
      @discriminatorName("type")
      sealed trait Body derives Schema
      object Body {
        @caseName("TEXT")
        final case class Text(content: NonEmptyString) extends Body

        @caseName("JSON")
        final case class Json(content: NonEmptyString) extends Body

        @caseName("XML")
        final case class Xml(content: NonEmptyString) extends Body
      }
    }
  }

  private val testEndpoint =
    (Endpoint(RoutePattern.POST / "test") ?? Doc.p("Test a Source"))
      .outErrors[Error](
        HttpCodec.error[Error.Error0](zio.http.Status.BadRequest),
        HttpCodec.error[Error.Error1](zio.http.Status.InternalServerError),
      )
      .out[Output](MediaType.application.json)
      .in[Input]

  override val spec =
    suite("OpenAPIGen")(
      suite(".gen")(
        test("doesn't throw 'ClassCastException: class zio.schema.Schema$Lazy cannot be cast to class zio.schema.Schema$Record'") {
          zio.http.endpoint.openapi.OpenAPIGen.gen(endpoint = testEndpoint)
          assertTrue(true)
        },
        test("scala doc for api doc is sanetized") {
          /**
           * This is the Input documentation
           */
          final case class Input(a: String)

          implicit val schema: Schema[Input] = DeriveSchema.gen[Input]

          val testEndpoint =
            (Endpoint(RoutePattern.POST / "test") ?? Doc.p("This is my 'POST /test' endpoint doc"))
              .in[Input]
              .out[String](mediaType = MediaType.application.json, doc = Doc.p("this is the output doc"))

          val spec: String =
            OpenAPIGen.fromEndpoints(
              title = "This is my OpenAPI doc title",
              version = "0.0.0",
              endpoints = List(testEndpoint)
              ).toJson

          assertTrue(spec.contains(""""description":"This is the Input documentation""""))
        },
        test("JsonSchema correctly handles @format annotation for string types") {
          final case class EmailData(@format(Email) email: String, received: Option[java.time.LocalDate]) derives Schema
          val js     = JsonSchema.fromZSchema(EmailData.derived$Schema)

          val oapi = OpenAPI.empty.copy(
            components = Some(
              OpenAPI.Components(
                schemas = ListMap(OpenAPI.Key.fromString("EmailData").get -> ReferenceOr.Or(js)),
              ),
            ),
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
                           |      "EmailData" : {
                           |        "type" : "object",
                           |        "properties" : {
                           |          "email" : {
                           |            "type" : "string",
                           |            "format" : "email"
                           |          },
                           |         "received" : {
                           |            "type" : ["string", "null"],
                           |            "format" : "date"
                           |          }
                           |        },
                           |        "required" : [
                           |          "email"
                           |        ]
                           |      }
                           |    }
                           |  }
                           |}""".stripMargin

          assertTrue(toJsonAst(json) == toJsonAst(expected))
        }
      )
    )
}
