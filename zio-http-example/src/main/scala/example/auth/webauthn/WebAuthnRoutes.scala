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

      // Mobile device endpoints
      Method.GET / "api" / "devices"                   -> handler {
        for {
          deviceManager <- ZIO.service[MobileDeviceManager]
          devices       <- deviceManager.getConnectedDevices()
        } yield Response.json(devices.toJson)
      },
      Method.POST / "api" / "devices" / "register"     -> handler { (req: Request) =>
        for {
          deviceManager <- ZIO.service[MobileDeviceManager]
          body          <- req.body.asString.orDie
          deviceInfo    <- ZIO
            .fromEither(body.fromJson[MobileDeviceInfo])
            .mapError(e => Response.badRequest(s"Invalid JSON: $e"))
          _             <- deviceManager.registerDevice(deviceInfo)
        } yield Response.json("""{"success": true}""")
      },
      Method.POST / "api" / "keyexchange" / "initiate" -> handler { (req: Request) =>
        for {
          deviceManager <- ZIO.service[MobileDeviceManager]
          body          <- req.body.asString.orDie
          json          <- ZIO
            .fromEither(body.fromJson[Map[String, String]])
            .mapError(e => Response.badRequest(s"Invalid JSON: $e"))
          deviceId      <- ZIO
            .fromOption(json.get("deviceId"))
            .orElseFail(Response.badRequest("Missing deviceId"))
          challenge = java.util.UUID.randomUUID().toString
          _ <- deviceManager
            .initiateKeyExchange(deviceId, challenge)
            .mapError(e => Response.badRequest(e))
        } yield Response.json(s"""{"challenge": "$challenge"}""")
      },
      Method.POST / "api" / "keyexchange" / "verify"   -> handler { (req: Request) =>
        for {
          deviceManager <- ZIO.service[MobileDeviceManager]
          body          <- req.body.asString.orDie
          request       <- ZIO
            .fromEither(body.fromJson[MobileKeyExchangeRequest])
            .mapError(e => Response.badRequest(s"Invalid JSON: $e"))
          response      <- deviceManager
            .verifyKeyExchange(request)
            .mapError(e => Response.badRequest(e))
        } yield Response.json(response.toJson)
      },

      // WebSocket endpoint for mobile devices
      Method.GET / "ws" / "mobile" -> handler { (_: Request) =>
        for {
          deviceManager <- ZIO.service[MobileDeviceManager]
          r             <- Response.fromSocketApp(WebSocketHandler.mobileDeviceHandler(deviceManager))
        } yield r
      },
    ) @@ Middleware.cors

}
