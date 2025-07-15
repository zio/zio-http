package example.auth.webauthn
import zio._
import zio.http._
import zio.json._

import java.nio.charset.StandardCharsets
import scala.io.Source

// ============================================================================
// HTTP Routes
// ============================================================================

object WebAuthnRoutes {

  /**
   * Loads HTML content from the resources directory
   */
  def loadHtmlFromResources(resourcePath: String): ZIO[Any, Throwable, String] = {
    ZIO.attempt {
      val inputStream = getClass.getResourceAsStream(resourcePath)
      if (inputStream == null) throw new RuntimeException(s"Resource not found: $resourcePath")

      val source = Source.fromInputStream(inputStream, StandardCharsets.UTF_8.name())
      try source.mkString
      finally {
        source.close()
        inputStream.close()
      }
    }
  }

  def routes =
    Routes(
      // Serve static files
      Method.GET / Root -> handler { (_: Request) =>
        for {
          html <- loadHtmlFromResources("/webauthn-client.html").orDie
        } yield Response(
          status = Status.Ok,
          headers = Headers(Header.ContentType(MediaType.text.html)),
          body = Body.fromString(html),
        )
      },

      // WebAuthn API endpoints
      Method.POST / "api" / "webauthn" / "registration" / "start"    -> handler { (req: Request) =>
        for {
          service  <- ZIO.service[WebAuthnService]
          body     <- req.body.asString.orDie
          request  <- ZIO
            .fromEither(body.fromJson[StartRegistrationRequest])
            .mapError(e => Response.badRequest(s"Invalid JSON: $e"))
          response <- service
            .startRegistration(request)
            .mapError(e => Response.internalServerError(e))
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "registration" / "finish"   -> handler { (req: Request) =>
        for {
          service  <- ZIO.service[WebAuthnService]
          body     <- req.body.asString.orDie
          request  <- ZIO
            .fromEither(body.fromJson[FinishRegistrationRequest])
            .mapError(e => Response.badRequest(s"Invalid JSON: $e"))
          response <- service
            .finishRegistration(request)
            .mapError(e => Response.internalServerError(e))
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "authentication" / "start"  -> handler { (req: Request) =>
        for {
          service  <- ZIO.service[WebAuthnService]
          body     <- req.body.asString.orDie
          request  <- ZIO
            .fromEither(body.fromJson[StartAuthenticationRequest])
            .mapError(e => Response.badRequest(s"Invalid JSON: $e"))
          response <- service
            .startAuthentication(request)
            .mapError(e => Response.internalServerError(e))
        } yield Response.json(response.toJson)
      },
      Method.POST / "api" / "webauthn" / "authentication" / "finish" -> handler { (req: Request) =>
        for {
          service  <- ZIO.service[WebAuthnService]
          body     <- req.body.asString.orDie
          request  <- ZIO
            .fromEither(body.fromJson[FinishAuthenticationRequest])
            .mapError(e => Response.badRequest(s"Invalid JSON: $e"))
          response <- service
            .finishAuthentication(request)
            .mapError(e => Response.internalServerError(e))
        } yield Response.json(response.toJson)
      },
    ) @@ Middleware.cors

}
