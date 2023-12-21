/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import zio.test.TestAspect.withLiveClock
import zio.test._
import zio.{Chunk, Scope, ZIO, ZLayer}

import zio.http.URL.Location

object ZClientAspectSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status

  val app: HttpApp[Any] = {
    Route.handled(Method.GET / "hello")(Handler.response(Response.text("hello")))
  }.toHttpApp

  val redir: HttpApp[Any] = {
    Route.handled(Method.GET / "redirect")(Handler.response(Response.redirect(URL.empty / "hello")))
  }.toHttpApp

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZClientAspect")(
      test("debug") {
        for {
          port       <- Server.install(app)
          baseClient <- ZIO.service[Client]
          client = baseClient.url(
            URL(Path.empty, Location.Absolute(Scheme.HTTP, "localhost", Some(port))),
          ) @@ ZClientAspect.debug
          response <- client.request(Request.get(URL.empty / "hello"))
          output   <- TestConsole.output
        } yield assertTrue(
          extractStatus(response) == Status.Ok,
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
              URL(Path.empty, Location.Absolute(Scheme.HTTP, "localhost", Some(port))),
            )
            .disableStreaming @@ ZClientAspect.requestLogging(
            loggedRequestHeaders = Set(Header.UserAgent),
            logResponseBody = true,
          )
          response <- client.request(Request.get(URL.empty / "hello"))
          output   <- ZTestLogger.logOutput
          messages    = output.map(_.message())
          annotations = output.map(_.annotations)
        } yield assertTrue(
          extractStatus(response) == Status.Ok,
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
      test("followRedirects")(
        for {
          port       <- Server.install(redir ++ app)
          baseClient <- ZIO.service[Client]
          client = baseClient
            .url(
              URL(Path.empty, Location.Absolute(Scheme.HTTP, "localhost", Some(port))),
            )
            .disableStreaming @@ ZClientAspect.followRedirects(2)((resp, message) => ZIO.logInfo(message).as(resp))
          response <- client.request(Request.get(URL.empty / "redirect"))
        } yield assertTrue(
          extractStatus(response) == Status.Ok,
        ),
      ),
    ).provide(
      ZLayer.succeed(Server.Config.default.onAnyOpenPort),
      Server.live,
      Client.default,
      Scope.default,
    ) @@ withLiveClock
}
