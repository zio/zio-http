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

package zio.http.middleware

import zio.test.Assertion.hasSubset
import zio.test._

import zio.http.HttpAppMiddleware.cors
import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.middleware.Cors.CorsConfig
import zio.http.model._

object CorsSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  val app = Handler.ok.toHttp @@ cors(CorsConfig(allowedMethods = Some(Set(Method.GET))))

  override def spec = suite("CorsMiddlewares")(
    test("OPTIONS request") {
      val request = Request
        .options(URL(!! / "success"))
        .copy(
          headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("test-env"),
        )

      val initialHeaders = Headers
        .accessControlAllowCredentials(true)
        .withAccessControlAllowMethods(Method.GET)
        .withAccessControlAllowOrigin("test-env")

      val expected = CorsConfig().allowedHeaders
        .fold(Headers.empty) { h =>
          h
            .map(value => Headers.empty.withAccessControlAllowHeaders(value))
            .fold(initialHeaders)(_ ++ _)
        }
        .toList
      for {
        res <- app.runZIO(request)
      } yield assert(res.headersAsList)(hasSubset(expected)) &&
        assertTrue(res.status == Status.NoContent)

    },
    test("GET request") {
      val request =
        Request
          .get(URL(!! / "success"))
          .copy(
            headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("test-env"),
          )

      val expected = Headers
        .accessControlExposeHeaders("*")
        .withAccessControlAllowOrigin("test-env")
        .withAccessControlAllowMethods(Method.GET)
        .withAccessControlAllowCredentials(true)
        .toList

      for {
        res <- app.runZIO(request)
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
  )
}
