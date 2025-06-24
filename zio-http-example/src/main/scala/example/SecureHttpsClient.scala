package example

import zio._
import zio.http._
import zio.http.netty.NettyConfig
import zio.json._

object SecureHttpsClient extends ZIOAppDefault {

  case class Greeting(greetings: String)
  object Greeting {
    implicit val decoder: JsonDecoder[Greeting] =
      DeriveJsonDecoder.gen[Greeting]
  }

  val app: ZIO[Client, Throwable, Unit] =
    for {
      _ <- Console.printLine("Making secure HTTPS requests...")

      textResponse <- Client.batched(
        Request.get("https://localhost:8090/text"),
      )
      textBody     <- textResponse.body.asString
      _            <- Console.printLine(s"Text response: $textBody")

      jsonResponse <- Client.batched(
        Request.get("https://localhost:8090/json"),
      )
      jsonBody     <- jsonResponse.body.asString
      _            <- Console.printLine(s"JSON response: $jsonBody")

      greeting <- ZIO
        .fromEither(jsonBody.fromJson[Greeting])
        .orElse(Console.printLine("Failed to parse JSON response"))
      _        <- Console.printLine(s"Parsed greeting: $greeting")
    } yield ()

  override val run =
    app.provide(
      ZLayer.succeed {
        ZClient.Config.default.ssl(
          ClientSSLConfig.FromCertResource(
            "server.crt",
          ),
        )
      },
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
      ZClient.live,
    )

}
