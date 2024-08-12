package zio.http.endpoint.openapi

import zio.test._

import zio.schema.{Schema, derived}

import zio.schema.DeriveSchema
import zio.test._
import zio.http.endpoint.Endpoint
import zio.http.codec.{Doc, HttpCodec}
import zio.http.endpoint.Endpoint
import zio.http.{MediaType, RoutePattern}
import zio.schema.annotation.{caseName, discriminatorName}
import zio.prelude.Subtype

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
      )
    )
}
