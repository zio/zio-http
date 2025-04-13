package example

import zio._
import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.schema.{DeriveSchema, Schema}

object ContentNegotiationExample extends ZIOAppDefault {
  case class Greeting(name: String)
  object Greeting {
    implicit val schema: Schema[Greeting] = DeriveSchema.gen
  }

  case class ErrorResponse(message: String, code: String)
  object ErrorResponse {
    implicit val schema: Schema[ErrorResponse] = DeriveSchema.gen
  }

  val jsonCodec = HttpCodec.content[Greeting](MediaType.application.json)
  val textCodec = HttpCodec.content[Greeting](MediaType.text.plain)

  val errorJsonCodec = HttpCodec.content[ErrorResponse](MediaType.application.json)
  val errorTextCodec = HttpCodec.content[ErrorResponse](MediaType.text.plain)

  val negotiatedSuccessCodec = HttpCodec.negotiated[Greeting](jsonCodec, textCodec)
  val negotiatedErrorCodec = HttpCodec.negotiated[ErrorResponse](errorJsonCodec, errorTextCodec)

  val mediaTypeJsonOrTextEndpoint = Endpoint(RoutePattern.POST / "api" / "mediaTypeJsonOrText")
    .out[Greeting](negotiatedSuccessCodec)
    .outError[ErrorResponse](negotiatedErrorCodec, Status.BadRequest)

  val routes = mediaTypeJsonOrTextEndpoint.implement { _ =>
    Random.nextBoolean.flatMap { success =>
      if (success) {
        ZIO.succeed(Greeting(name = "greeting"))
      } else {
        ZIO.fail(ErrorResponse(message = "Something went wrong", code = "ERR_001"))
      }
    }
  }

  val app = routes.sandbox

  override val run = Server.serve(app).provide(Server.default)
}