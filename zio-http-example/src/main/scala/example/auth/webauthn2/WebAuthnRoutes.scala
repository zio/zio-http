package example.auth.webauthn2

import example.auth.webauthn2.models.JsonCodecs._
import example.auth.webauthn2.models._
import zio._
import zio.http._
import zio.json._

/**
 * HTTP routes for WebAuthn endpoints
 */
object WebAuthnRoutes {

  def apply(service: WebAuthnService): Routes[Any, Response] =
    Routes(
      // Serve the HTML client
      Method.GET / Root -> Handler
        .fromResource("webauthn2-client.html")
        .orElse(Handler.internalServerError("Failed to load HTML")),

      // Registration endpoints
      Method.POST / "api" / "webauthn" / "registration" / "start"  -> handler { (req: Request) =>
        for {
          body     <- req.body.asString
          request  <- ZIO.fromEither(body.fromJson[RegistrationStartRequest])
          response <- service.startRegistration(request.username)
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "registration" / "finish" -> handler { (req: Request) =>
        for {
          body    <- req.body.asString
          request <- ZIO.fromEither(body.fromJson[RegistrationFinishRequest])
          result  <- service.finishRegistration(request)
        } yield Response.json(result.toJson)
      },

      // Authentication endpoints
      Method.POST / "api" / "webauthn" / "authentication" / "start"  -> handler { (req: Request) =>
        for {
          body     <- req.body.asString
          request  <- ZIO.fromEither(body.fromJson[AuthenticationStartRequest])
          response <- service.startAuthentication(request.username)
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "authentication" / "finish" -> handler { (req: Request) =>
        for {
          body    <- req.body.asString
          request <- ZIO.fromEither(body.fromJson[AuthenticationFinishRequest])
          result  <- service.finishAuthentication(request)
        } yield Response.json(result.toJson) // TODO: model failure response
      },
    ).sandbox @@ Middleware.cors @@ Middleware.debug
}
