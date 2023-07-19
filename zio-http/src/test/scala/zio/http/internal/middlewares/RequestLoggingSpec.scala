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

import zio.test._
import zio.{Scope, ZIO}

import zio.http.Middleware.requestLogging
import zio.http._
import zio.http.internal.HttpAppTestExtensions

object RequestLoggingSpec extends ZIOSpecDefault with HttpAppTestExtensions {

  private val app = Routes(
    Method.GET / "ok"     -> Handler.ok,
    Method.GET / "error"  -> Handler.error(HttpError.InternalServerError()),
    Method.GET / "fail"   -> Handler.fail(Response.status(Status.Forbidden)),
    Method.GET / "defect" -> Handler.die(new Throwable("boom")),
  ).sandbox.toHttpApp

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("RequestLoggingSpec")(
      test("logs successful request") {
        for {
          _       <- (app @@ requestLogging()).runZIO(Request.get(url = URL(Root / "ok")))
          entries <- ZTestLogger.logOutput
          first = entries.head
        } yield assertTrue(
          first.message() == "Http request served",
          first.annotations == Map(
            "method"        -> "GET",
            "duration_ms"   -> "0",
            "url"           -> "/ok",
            "response_size" -> "0",
            "status_code"   -> "200",
            "request_size"  -> "0",
          ),
        )
      },
      test("logs successful request with different status code") {
        for {
          _       <- (app @@ requestLogging()).runZIO(Request.get(url = URL(Root / "error")))
          entries <- ZTestLogger.logOutput
          first = entries.head
        } yield assertTrue(
          first.message() == "Http request served",
          first.annotations == Map(
            "method"        -> "GET",
            "duration_ms"   -> "0",
            "url"           -> "/error",
            "response_size" -> "0",
            "status_code"   -> "500",
            "request_size"  -> "0",
          ),
        )
      },
      test("logs request failing with an error response") {
        for {
          _       <- (app @@ requestLogging()).runZIO(Request.get(url = URL(Root / "fail"))).ignore
          entries <- ZTestLogger.logOutput
          first = entries.head
        } yield assertTrue(
          first.message() == "Http request failed",
          first.annotations == Map(
            "method"        -> "GET",
            "duration_ms"   -> "0",
            "url"           -> "/fail",
            "response_size" -> "0",
            "status_code"   -> "403",
            "request_size"  -> "0",
          ),
        )
      },
      test("logs request resulting in a defect") {
        for {
          _       <- (app @@ requestLogging())
            .runZIO(Request.get(url = URL(Root / "defect")))
            .ignore
            .catchAllDefect(_ => ZIO.unit)
          entries <- ZTestLogger.logOutput
          first = entries.head
        } yield assertTrue(
          first.message() == "Http request failed",
          first.annotations == Map(
            "method"        -> "GET",
            "duration_ms"   -> "0",
            "url"           -> "/defect",
            "response_size" -> "0",
            "status_code"   -> "500",
            "request_size"  -> "0",
          ),
        )
      },
    )
}
