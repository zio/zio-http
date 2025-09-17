package example.auth.webauthn2

import com.yubico.webauthn.data.{
  AuthenticatorAttestationResponse,
  ClientRegistrationExtensionOutputs,
  PublicKeyCredential,
}
import example.auth.webauthn2.models.JsonCodecs._
import example.auth.webauthn2.models._
import zio._
import zio.http._
import zio.json._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

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
          body     <- req.body.asString.debug("1")
          request  <- ZIO.fromEither(body.fromJson[RegistrationStartRequest]).debug("2")
          response <- service.startRegistration(request.username)
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "registration" / "finish" -> handler { (req: Request) =>
        for {
          body <- req.body.asString
          req  <- ZIO.fromEither(body.fromJson[RegistrationFinishRequest]).flatMapError(x => ZIO.debug("error: "  + x))
          result <- service.finishRegistration(req.publicKeyCredential, req.username)
        } yield Response.text(result)
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
          body    <- req.body.asString.debug("a")
          request <- ZIO.fromEither(body.fromJson[AuthenticationFinishRequest]).debug("b")
          result  <- service.finishAuthentication(request).debug("3")
        } yield Response.text(result)
      },
    ).sandbox @@ Middleware.cors
}
