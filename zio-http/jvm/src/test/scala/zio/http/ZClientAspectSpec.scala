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

import scala.annotation.nowarn

import zio._
import zio.test.TestAspect.withLiveClock
import zio.test._

import zio.http.URL.Location
import zio.http.netty.NettyConfig

object ZClientAspectSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status

  val routes: Routes[Any, Response] =
    Route.handled(Method.GET / "hello")(Handler.fromResponse(Response.text("hello"))).toRoutes

  val redir: Routes[Any, Response] =
    Route.handled(Method.GET / "redirect")(Handler.fromResponse(Response.redirect(URL.empty / "hello"))).toRoutes

  @nowarn("msg=possible missing interpolator")
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ZClientAspect")(
      test("debug") {
        for {
          port       <- Server.installRoutes(routes)
          baseClient <- ZIO.service[Client]
          client = baseClient.url(
            URL(Path.empty, Location.Absolute(Scheme.HTTP, "localhost", Some(port))),
          ) @@ ZClientAspect.debug
          response <- client.batched(Request.get(URL.empty / "hello"))
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
          port       <- Server.installRoutes(routes)
          baseClient <- ZIO.service[Client]
          client = baseClient
            .url(
              URL(Path.empty, Location.Absolute(Scheme.HTTP, "localhost", Some(port))),
            )
            .batched @@ ZClientAspect.requestLogging(
            loggedRequestHeaders = Set(Header.UserAgent),
            logResponseBody = true,
          )
          response <- client(Request.get(URL.empty / "hello"))
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
          port       <- Server.installRoutes(redir ++ routes)
          baseClient <- ZIO.service[Client]
          client = baseClient
            .url(
              URL(Path.empty, Location.Absolute(Scheme.HTTP, "localhost", Some(port))),
            )
            .batched @@ ZClientAspect.followRedirects(2)((resp, message) => ZIO.logInfo(message).as(resp))
          response <- client.request(Request.get(URL.empty / "redirect"))
        } yield assertTrue(
          extractStatus(response) == Status.Ok,
        ),
      ),
      test("curl request logger") {

        for {
          port       <- Server.install(routes)
          baseClient <- ZIO.service[Client]
          client = baseClient
            .url(
              URL(Path.empty, Location.Absolute(Scheme.HTTP, "localhost", Some(port))),
            )
            .batched @@ ZClientAspect.curlLogger(logEffect = m => Console.printLine(m).orDie)
          response <- client.request(Request.get(URL.empty / "hello"))
          output   <- TestConsole.output
        } yield assertTrue(
          output.mkString("") ==
            s"""curl \\
               |  --verbose \\
               |  --request GET \\
               |  --header 'user-agent:${Client.defaultUAHeader.renderedValue}' \\
               |  'http://localhost:${port}/hello'
               |""".stripMargin,
          extractStatus(response) == Status.Ok,
        )
      },
    ).provide(
      ZLayer.succeed(Server.Config.default.onAnyOpenPort),
      Server.customized,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      Client.default,
    ) @@ withLiveClock
}
