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
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{diagnose, sequential, shrinks, withLiveClock}
import zio.test.{assertTrue, assertZIO}

import zio.http.ServerSpec.requestBodySpec
import zio.http.internal.{DynamicServer, HttpRunnableSpec}
import zio.http.netty.NettyConfig

object HybridRequestStreamingServerSpec extends HttpRunnableSpec {
  def extractStatus(res: Response): Status = res.status

  private val MaxSize = 1024 * 10

  private val configAppWithHybridRequestStreaming =
    Server.Config.default
      .port(0)
      .requestDecompression(true)
      .hybridRequestStreaming(MaxSize)

  private val appWithHybridReqStreaming = serve

  /**
   * Generates a string of the provided length and char.
   */
  private def genString(size: Int, char: Char): String = {
    val buffer = new Array[Char](size)
    for (i <- 0 until size) buffer(i) = char
    new String(buffer)
  }

  val hybridStreamingServerSpec = suite("HybridStreamingServerSpec")(
    test("small content should stream") {
      val size    = MaxSize - 1
      val content = genString(size, '?')
      val routes  = Handler
        .fromFunctionZIO[Request] {
          _.body.asStream.runCount
            .map(bytesCount => Response.text(bytesCount.toString))
        }
        .sandbox
        .toRoutes

      val res = routes.deploy(Request(body = Body.fromString(content))).flatMap(_.body.asString)
      assertZIO(res)(equalTo(size.toString))
    },
    test("large content should chunk") {
      val size    = MaxSize + 1
      val content = genString(size, '?')
      val routes  = Handler
        .fromFunctionZIO[Request] {
          _.body.asStream.runCount
            .map(bytesCount => Response.text(bytesCount.toString))
        }
        .sandbox
        .toRoutes

      val res = routes.deploy(Request(body = Body.fromString(content))).flatMap(_.body.asString)
      assertZIO(res)(equalTo(size.toString))
    },
    test("boundary case") {
      val size    = MaxSize
      val content = genString(size, '?')
      val routes  = Handler
        .fromFunctionZIO[Request] {
          _.body.asStream.runCount
            .map(bytesCount => Response.text(bytesCount.toString))
        }
        .sandbox
        .toRoutes

      val res = routes.deploy(Request(body = Body.fromString(content))).flatMap(_.body.asString)
      assertZIO(res)(equalTo(size.toString))
    },
  )

  override def spec =
    suite("HybridRequestStreamingServerSpec") {
      suite("app with hybrid request streaming") {
        appWithHybridReqStreaming.as(List(requestBodySpec, hybridStreamingServerSpec))
      }
    }.provideSome[DynamicServer & Server & Client](Scope.default)
      .provideShared(
        DynamicServer.live,
        ZLayer.succeed(configAppWithHybridRequestStreaming),
        Server.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
        Client.default,
      ) @@ diagnose(15.seconds) @@ sequential @@ shrinks(0) @@ withLiveClock
}
