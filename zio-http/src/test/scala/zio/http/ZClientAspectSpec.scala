package zio.http

import zio.test.TestAspect.withLiveClock
import zio.test._
import zio.{Chunk, Scope, ZIO, ZLayer}

import zio.http.URL.Location

object ZClientAspectSpec extends ZIOSpecDefault {

  val app: App[Any] = Handler.fromFunction[Request] { _ => Response.text("hello") }.toHttp

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZClientAspect")(
      test("debug") {
        for {
          port       <- Server.install(app)
          baseClient <- ZIO.service[Client]
          client = baseClient.url(
            URL(Path.empty, Location.Absolute(Scheme.HTTP, "localhost", port)),
          ) @@ ZClientAspect.debug
          response <- client.get("/hello")
          output   <- TestConsole.output
        } yield assertTrue(
          response.status == Status.Ok,
          output.size == 1,
          output.head.startsWith(s"200 GET http://localhost:$port/hello"),
          output.head.endsWith("ms\n"),
        )
      },
      test("requestLogging")(
        for {
          port       <- Server.install(app)
          baseClient <- ZIO.service[Client]
          client = baseClient
            .url(
              URL(Path.empty, Location.Absolute(Scheme.HTTP, "localhost", port)),
            )
            .withDisabledStreaming @@ ZClientAspect.requestLogging(
            loggedRequestHeaders = Set(Header.UserAgent),
            logResponseBody = true,
          )
          response <- client.get("/hello")
          output   <- ZTestLogger.logOutput
          messages    = output.map(_.message())
          annotations = output.map(_.annotations)
        } yield assertTrue(
          response.status == Status.Ok,
          messages == Chunk("Http client request"),
          annotations.size == 1,
          (annotations.head - "duration_ms") ==
            Map(
              "response_size" -> "5",
              "request_size"  -> "0",
              "status_code"   -> "200",
              "method"        -> "GET",
              "url"           -> s"http://localhost:$port/hello",
              "user-agent"    -> Client.defaultUAHeader.renderedValue,
              "response"      -> "hello",
            ),
          annotations.head.contains("duration_ms"),
        ),
      ),
    ).provide(
      ZLayer.succeed(Server.Config.default.onAnyOpenPort),
      Server.live,
      Client.default,
    ) @@ withLiveClock
}
