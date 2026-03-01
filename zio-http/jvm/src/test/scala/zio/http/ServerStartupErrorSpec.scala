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

import java.net.ServerSocket

import zio._
import zio.test._

import zio.http.netty.NettyConfig

object ServerStartupErrorSpec extends ZIOSpecDefault {

  private val routes = Routes(
    Method.GET / "ok" -> Handler.ok,
  )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerStartupErrorSpec")(
      test("logs error when port is already in use") {
        ZIO
          .acquireRelease(ZIO.attemptBlocking(new ServerSocket(0)))(s => ZIO.attemptBlocking(s.close()).ignore)
          .flatMap { socket =>
            val port = socket.getLocalPort
            Server
              .installRoutes(routes)
              .provide(
                Server.customized,
                ZLayer.succeed(Server.Config.default.port(port)),
                ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
              )
              .exit
              .flatMap { exit =>
                ZTestLogger.logOutput.map { entries =>
                  val errorLog = entries.find(_.message() == "Failed to start server")
                  assertTrue(
                    exit.isFailure,
                    errorLog.isDefined,
                    errorLog.get.logLevel == LogLevel.Error,
                  )
                }
              }
          }
      },
    ) @@ TestAspect.withLiveClock
}
