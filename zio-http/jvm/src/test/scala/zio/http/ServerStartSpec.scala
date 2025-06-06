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

import zio.test.Assertion.{equalTo, not}
import zio.test.TestAspect.{flaky, withLiveClock}
import zio.test._
import zio.{Scope, ZIO, ZLayer}

import zio.http.internal.{DynamicServer, RoutesRunnableSpec}
import zio.http.netty.NettyConfig

object ServerStartSpec extends RoutesRunnableSpec {

  def serverStartSpec = suite("ServerStartSpec")(
    test("desired port") {
      val port   = 8088
      val config = Server.Config.default.port(port)
      serve(Routes.empty).flatMap { port =>
        assertZIO(ZIO.attempt(port))(equalTo(port))
      }.provide(
        ZLayer.succeed(config),
        DynamicServer.live,
        Server.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      )
    },
    test("available port") {
      val port   = 0
      val config = Server.Config.default.port(port)
      serve(Routes.empty).flatMap { bindPort =>
        assertZIO(ZIO.attempt(bindPort))(not(equalTo(port)))
      }.provide(
        ZLayer.succeed(config),
        DynamicServer.live,
        Server.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      )
    },
    test("application can shutdown if server is not started") {
      ZIO
        .succeed(assertCompletes)
        .provide(
          Server.customized.unit,
          ZLayer.succeed(Server.Config.default.port(8089)),
          ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
        )
    } @@ flaky,
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = serverStartSpec @@ withLiveClock
}
