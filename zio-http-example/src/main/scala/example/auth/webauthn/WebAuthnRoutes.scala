package example.auth.webauthn

import example.auth.webauthn.core._
import example.auth.webauthn.model._
import zio._
import zio.http._
import zio.json._
import zio.schema.codec.JsonCodec.zioJsonBinaryCodec

/**
 * HTTP routes for WebAuthn endpoints
 */
object WebAuthnRoutes {
  def apply(service: WebAuthnServiceImpl): Routes[Any, Response] =
    Routes(
      // Serve the HTML client
      Method.GET / Root -> Handler
        .fromResource("webauthn-client.html")
        .orElse(Handler.internalServerError("Failed to load HTML")),

      // Registration endpoints
      Method.POST / "api" / "webauthn" / "registration" / "start"  -> handler { (req: Request) =>
        for {
          request  <- req.body.to[RegistrationStartRequest]
          response <- service.startRegistration(request.username)
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "registration" / "finish" -> handler { (req: Request) =>
        for {
          body    <- req.body.asString
          request <- ZIO.fromEither(body.fromJson[RegistrationFinishRequest])
          result  <- service.finishRegistration(request).mapError {
            case NoRegistrationRequestFound(username) =>
              Response(
                status = Status.NotFound,
                body = Body.fromString(s"No registration request found for user: $username"),
              )

            case _ =>
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
          response <- service.startAuthentication(request.username)
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "authentication" / "finish" -> handler { (req: Request) =>
        for {
          body    <- req.body.asString
          request <- ZIO.fromEither(body.fromJson[AuthenticationFinishRequest])
          result  <- service.finishAuthentication(request).mapError {
            case NoAuthenticationRequestFound(challenge) =>
              Response(
                status = Status.NotFound,
                body = Body.fromString(s"No registration request found for challenge: $challenge"),
              )
            case _                                       =>
              Response(
                status = Status.Unauthorized,
                body = Body.fromString(s"Authentication failed!"),
              )
          }
        } yield Response(body = Body.from(result))
      },
    ).sandbox @@ Middleware.cors @@ Middleware.debug
}
