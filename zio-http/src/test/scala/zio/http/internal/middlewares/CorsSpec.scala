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

package zio.http.internal.middlewares

import zio.ZIO
import zio.test._

import zio.http.Header.AccessControlAllowMethods
import zio.http.RoutesAspect.{CorsConfig, cors}
import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.internal.middlewares.CorsSpec.app

object CorsSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  def extractStatus(response: Response): Status = response.status

  val app = Routes(
    Method.GET / "success" -> handler(Response.ok),
    Method.GET / "failure" -> handler(ZIO.fail("failure")),
    Method.GET / "die"     -> handler(ZIO.dieMessage("die")),
  ).handleErrorCause { cause =>
    Response(Status.InternalServerError, body = Body.fromString(cause.prettyPrint))
  }.toHttpApp @@ cors(CorsConfig(allowedMethods = AccessControlAllowMethods(Method.GET)))

  override def spec = suite("CorsSpec")(
    test("OPTIONS request") {
      val request = Request
        .options(URL(Root / "success"))
        .copy(
          headers = Headers(Header.AccessControlRequestMethod(Method.GET), Header.Origin("http", "test-env")),
        )

      for {
        res <- app.runZIO(request)
      } yield assertTrue(
        extractStatus(res) == Status.NoContent,
        res.hasHeader(Header.AccessControlAllowCredentials.Allow),
        res.hasHeader(Header.AccessControlAllowMethods(Method.GET)),
        res.hasHeader(Header.AccessControlAllowOrigin("http", "test-env")),
        res.hasHeader(Header.AccessControlAllowHeaders.All),
      )

    },
    test("GET request") {
      val request =
        Request
          .get(URL(Root / "success"))
          .copy(
            headers = Headers(Header.AccessControlRequestMethod(Method.GET), Header.Origin("http", "test-env")),
          )

      for {
        res <- app.runZIO(request)
      } yield assertTrue(
        res.hasHeader(Header.AccessControlExposeHeaders.All),
        res.hasHeader(Header.AccessControlAllowOrigin("http", "test-env")),
        res.hasHeader(Header.AccessControlAllowMethods(Method.GET)),
        res.hasHeader(Header.AccessControlAllowCredentials.Allow),
      )
    },
    test("GET request with server side failure") {
      val request =
        Request
          .get(URL(Root / "failure"))
          .copy(
            headers = Headers(Header.AccessControlRequestMethod(Method.GET), Header.Origin("http", "test-env")),
          )

      for {
        res <- app.runZIO(request)
      } yield assertTrue(
        res.hasHeader(Header.AccessControlExposeHeaders.All),
        res.hasHeader(Header.AccessControlAllowOrigin("http", "test-env")),
        res.hasHeader(Header.AccessControlAllowMethods(Method.GET)),
        res.hasHeader(Header.AccessControlAllowCredentials.Allow),
      )
    },
    test("GET request with server side defect") {
      val request =
        Request
          .get(URL(Root / "die"))
          .copy(
            headers = Headers(Header.AccessControlRequestMethod(Method.GET), Header.Origin("http", "test-env")),
          )

      for {
        res <- app.runZIO(request)
      } yield assertTrue(
        res.hasHeader(Header.AccessControlExposeHeaders.All),
        res.hasHeader(Header.AccessControlAllowOrigin("http", "test-env")),
        res.hasHeader(Header.AccessControlAllowMethods(Method.GET)),
        res.hasHeader(Header.AccessControlAllowCredentials.Allow),
      )
    },
  )
}
