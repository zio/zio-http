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
import zio.http.Middleware.{CorsConfig, cors}
import zio.http._
import zio.http.internal.HttpAppTestExtensions

object CorsSpec extends ZIOHttpSpec with HttpAppTestExtensions {
  def extractStatus(response: Response): Status = response.status

  val app = Routes(
    Method.GET / "success" -> handler(Response.ok),
    Method.GET / "failure" -> handler(ZIO.fail("failure")),
    Method.GET / "die"     -> handler(ZIO.dieMessage("die")),
  ).handleErrorCause { cause =>
    Response(Status.InternalServerError, body = Body.fromString(cause.prettyPrint))
  } @@ cors(CorsConfig(allowedMethods = AccessControlAllowMethods(Method.GET)))

  val appAllowAllHeaders = Routes(
    Method.GET / "success" -> handler(Response.ok),
  ).handleErrorCause { cause =>
    Response(Status.InternalServerError, body = Body.fromString(cause.prettyPrint))
  } @@ cors(
    CorsConfig(
      allowedOrigin = { case _ =>
        Some(Header.AccessControlAllowOrigin.All)
      },
      allowedMethods = Header.AccessControlAllowMethods.All,
      allowedHeaders = Header.AccessControlAllowHeaders.All,
    ),
  )

  val appNoServerHeaders = Routes(
    Method.GET / "success" -> handler(Response.ok),
  ).handleErrorCause { cause =>
    Response(Status.InternalServerError, body = Body.fromString(cause.prettyPrint))
  } @@ cors(
    CorsConfig(
      allowedOrigin = { case _ =>
        Some(Header.AccessControlAllowOrigin.All)
      },
      allowedMethods = Header.AccessControlAllowMethods.All,
      allowedHeaders = Header.AccessControlAllowHeaders.None,
    ),
  )

  override def spec = suite("CorsSpec")(
    test("OPTIONS request with allowAllHeaders server config") {
      val request =
        Request
          .options(URL(Path.root / "success"))
          .copy(
            headers = Headers(
              Header.Origin("http", "test-env"),
              Header.AccessControlRequestMethod(Method.GET),
            ),
          )

      for {
        res <- appAllowAllHeaders.runZIO(request)
      } yield assertTrue(
        extractStatus(res) == Status.NoContent,
        res.hasHeader(Header.AccessControlAllowCredentials.Allow),
        res.hasHeader(Header.AccessControlAllowHeaders.All),
      )
    },
    test("OPTIONS request with no headers allowed in server config") {
      val request =
        Request
          .options(URL(Path.root / "success"))
          .copy(
            headers = Headers(
              Header.Origin("http", "test-env"),
              Header.AccessControlRequestMethod(Method.GET),
            ),
          )

      for {
        res <- appNoServerHeaders.runZIO(request)
      } yield assertTrue(
        extractStatus(res) == Status.NoContent,
        res.hasHeader(Header.AccessControlAllowCredentials.Allow),
        !res.hasHeader(Header.AccessControlAllowHeaders.All),
      )
    },
    test("OPTIONS request") {
      val request = Request
        .options(URL(Path.root / "success"))
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
          .get(URL(Path.root / "success"))
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
          .get(URL(Path.root / "failure"))
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
          .get(URL(Path.root / "die"))
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
