package example.auth.webauthn

import zio._
import zio.json._

import zio.schema.codec.JsonCodec.zioJsonBinaryCodec

import zio.http._

import example.auth.webauthn.core._
import example.auth.webauthn.model._

/**
 * HTTP routes for WebAuthn endpoints
 */
object WebAuthnRoutes {
  def apply(webauthn: WebAuthnService): Routes[Any, Response] =
    Routes(
      // Serve the HTML client
      Method.GET / Root -> Handler
        .fromResource("webauthn-client.html")
        .orElse(Handler.internalServerError("Failed to load HTML")),

      // Registration endpoints
      Method.POST / "api" / "webauthn" / "registration" / "start"  -> handler { (req: Request) =>
        for {
          request  <- req.body.to[RegistrationStartRequest]
          response <- webauthn.startRegistration(request)
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "registration" / "finish" -> handler { (req: Request) =>
        for {
          body    <- req.body.asString
          request <- ZIO.fromEither(body.fromJson[RegistrationFinishRequest])
          result  <- webauthn.finishRegistration(request).orElseFail {
            Response(
              status = Status.InternalServerError,
              body = Body.fromString(s"Registration failed!"),
            )
          }
        } yield Response(body = Body.from(result))
      },

      // Authentication endpoints
      Method.POST / "api" / "webauthn" / "authentication" / "start"  -> handler { (req: Request) =>
        for {
          request  <- req.body.to[AuthenticationStartRequest]
          response <- webauthn.startAuthentication(request)
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "authentication" / "finish" -> handler { (req: Request) =>
        for {
          body    <- req.body.asString
          request <- ZIO.fromEither(body.fromJson[AuthenticationFinishRequest])
          result  <- webauthn
            .finishAuthentication(request)
            .orElseFail(
              Response(
                status = Status.Unauthorized,
                body = Body.fromString(s"Authentication failed!"),
              ),
            )
        } yield Response(body = Body.from(result))
      },
    ).sandbox @@ Middleware.cors @@ Middleware.debug
}
