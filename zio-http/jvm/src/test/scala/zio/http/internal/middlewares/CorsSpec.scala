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
import zio.http.internal.TestExtensions

object CorsSpec extends ZIOHttpSpec with TestExtensions {
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

  // App with restricted origin - only allows "http://allowed.com"
  // See: https://github.com/zio/zio-http/issues/3206
  val appRestrictedOrigin = Routes(
    Method.GET / "success"  -> handler(Response.ok),
    Method.POST / "success" -> handler(Response.ok),
  ).handleErrorCause { cause =>
    Response(Status.InternalServerError, body = Body.fromString(cause.prettyPrint))
  } @@ cors(
    CorsConfig(
      allowedOrigin = {
        case Header.Origin.Value(_, host, _) if host == "allowed.com" =>
          Some(Header.AccessControlAllowOrigin.Specific(Header.Origin.Value("http", host, None)))
        case _                                                        => None
      },
      allowedMethods = AccessControlAllowMethods(Method.GET, Method.POST),
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
    // Tests for https://github.com/zio/zio-http/issues/3206
    // CORS restrictions should apply to all HTTP methods, not just OPTIONS
    test("OPTIONS request from disallowed origin should be rejected") {
      val request =
        Request
          .options(URL(Path.root / "success"))
          .copy(
            headers = Headers(
              Header.Origin("http", "notallowed.com"),
              Header.AccessControlRequestMethod(Method.GET),
            ),
          )

      for {
        res <- appRestrictedOrigin.runZIO(request)
      } yield assertTrue(
        extractStatus(res) == Status.NotFound,
        !res.hasHeader(Header.AccessControlAllowOrigin.name),
      )
    },
    test("OPTIONS request from allowed origin should succeed") {
      val request =
        Request
          .options(URL(Path.root / "success"))
          .copy(
            headers = Headers(
              Header.Origin("http", "allowed.com"),
              Header.AccessControlRequestMethod(Method.GET),
            ),
          )

      for {
        res <- appRestrictedOrigin.runZIO(request)
      } yield assertTrue(
        extractStatus(res) == Status.NoContent,
        res.hasHeader(Header.AccessControlAllowOrigin("http", "allowed.com")),
      )
    },
    test("GET request from disallowed origin should be rejected - issue #3206") {
      // This test demonstrates the bug: non-OPTIONS requests from disallowed origins
      // are currently processed (just without CORS headers) instead of being rejected
      val request =
        Request
          .get(URL(Path.root / "success"))
          .copy(
            headers = Headers(
              Header.Origin("http", "notallowed.com"),
            ),
          )

      for {
        res <- appRestrictedOrigin.runZIO(request)
      } yield assertTrue(
        // The request from a disallowed origin should be rejected with 403 Forbidden
        extractStatus(res) == Status.Forbidden,
      )
    },
    test("POST request from disallowed origin should be rejected - issue #3206") {
      // Same bug for POST requests
      val request =
        Request
          .post(URL(Path.root / "success"), Body.empty)
          .copy(
            headers = Headers(
              Header.Origin("http", "notallowed.com"),
            ),
          )

      for {
        res <- appRestrictedOrigin.runZIO(request)
      } yield assertTrue(
        // The request from a disallowed origin should be rejected with 403 Forbidden
        extractStatus(res) == Status.Forbidden,
      )
    },
    test("GET request from allowed origin should succeed") {
      val request =
        Request
          .get(URL(Path.root / "success"))
          .copy(
            headers = Headers(
              Header.Origin("http", "allowed.com"),
            ),
          )

      for {
        res <- appRestrictedOrigin.runZIO(request)
      } yield assertTrue(
        extractStatus(res) == Status.Ok,
        res.hasHeader(Header.AccessControlAllowOrigin("http", "allowed.com")),
      )
    },
  )
}
