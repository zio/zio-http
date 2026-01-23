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

import zio._
import zio.test._

import zio.http.netty.NettyConfig

/**
 * Integration tests for server error logging behavior.
 *
 * These tests verify that when using sandbox on routes, unhandled errors are
 * properly logged (fixing issue #2491).
 */
object ServerErrorLoggingSpec extends ZIOSpecDefault {

  // Routes that die with an exception, sandboxed to convert to Response and log
  val routesDie = Routes(
    Method.GET / "die" -> Handler.die(new RuntimeException("test error")),
  ).sandbox

  // Routes that fail with a typed error, sandboxed to convert to Response and log
  val routesFail = Routes(
    Method.GET / "fail" -> Handler.fail(new RuntimeException("typed failure")),
  ).sandbox

  // Routes that succeed - should not produce error logs
  val routesOk = Routes(
    Method.GET / "ok" -> Handler.ok,
  ).sandbox

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerErrorLoggingSpec")(
      test("sandbox logs defects when server handles request") {
        for {
          port    <- Server.installRoutes(routesDie)
          _       <- ZIO.scoped {
            Client.streaming(Request.get(s"http://localhost:$port/die")).flatMap(_.ignoreBody)
          }
          entries <- ZTestLogger.logOutput
          errorLog = entries.find(_.message() == "Unhandled exception in request handler")
        } yield assertTrue(
          errorLog.isDefined,
          errorLog.get.logLevel == LogLevel.Error,
        )
      },
      test("sandbox logs typed failures when server handles request") {
        for {
          port    <- Server.installRoutes(routesFail)
          _       <- ZIO.scoped {
            Client.streaming(Request.get(s"http://localhost:$port/fail")).flatMap(_.ignoreBody)
          }
          entries <- ZTestLogger.logOutput
          errorLog = entries.find(_.message() == "Unhandled exception in request handler")
        } yield assertTrue(
          errorLog.isDefined,
          errorLog.get.logLevel == LogLevel.Error,
        )
      },
      test("sandbox does not log for successful requests") {
        for {
          port    <- Server.installRoutes(routesOk)
          _       <- ZIO.scoped {
            Client.streaming(Request.get(s"http://localhost:$port/ok")).flatMap(_.ignoreBody)
          }
          entries <- ZTestLogger.logOutput
          errorLog = entries.find(_.message() == "Unhandled exception in request handler")
        } yield assertTrue(errorLog.isEmpty)
      },
      test("server returns 500 status for sandboxed defects") {
        for {
          port     <- Server.installRoutes(routesDie)
          response <- ZIO.scoped {
            Client.streaming(Request.get(s"http://localhost:$port/die")).flatMap(_.ignoreBody)
          }
        } yield assertTrue(response.status == Status.InternalServerError)
      },
    ).provide(
      Server.customized,
      ZLayer.succeed(Server.Config.default),
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      Client.default,
    ) @@ TestAspect.sequential
}
