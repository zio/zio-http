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
import zio.test.TestAspect.{diagnose, sequential, shrinks, timeout, withLiveClock}
import zio.test.{assertCompletes, assertTrue, assertZIO}

import zio.http.ServerSpec.requestBodySpec
import zio.http.internal.{DynamicServer, HttpRunnableSpec}

object RequestStreamingServerSpec extends HttpRunnableSpec {
  def extractStatus(res: Response): Status = res.status

  private val configAppWithRequestStreaming =
    Server.Config.default
      .port(0)
      .requestDecompression(true)
      .enableRequestStreaming

  private val appWithReqStreaming = serve

  /**
   * Generates a string of the provided length and char.
   */
  private def genString(size: Int, char: Char): String = {
    val buffer = new Array[Char](size)
    for (i <- 0 until size) buffer(i) = char
    new String(buffer)
  }

  val streamingServerSpec = suite("ServerStreamingSpec")(
    test("test unsafe large content") {
      val size    = 1024 * 1024
      val content = genString(size, '?')
      val app     = Handler
        .fromFunctionZIO[Request] {
          _.body.asStream.runCount
            .map(bytesCount => Response.text(bytesCount.toString))
        }
        .sandbox
        .toHttpApp
      val res     = app.deploy(Request(body = Body.fromString(content))).flatMap(_.body.asString)
      assertZIO(res)(equalTo(size.toString))
    },
    test("multiple body read") {
      val app = Routes.singleton {
        handler { (_: Path, req: Request) =>
          for {
            _ <- req.body.asChunk
            _ <- req.body.asChunk
          } yield Response.ok
        }
      }.sandbox.toHttpApp
      val res = app.deploy(Request()).map(_.status)
      assertZIO(res)(equalTo(Status.InternalServerError))
    },
    suite("streaming request passed to client")({
      val app   = Routes(
        Method.POST / "1" -> handler { (req: Request) =>
          val host       = req.headers.get(Header.Host).get
          val newRequest =
            req.copy(url = req.url.path("/2").host(host.hostAddress).port(host.port.getOrElse(80)))
          ZIO.debug(s"#1: got response, forwarding") *>
            ZIO.serviceWithZIO[Client] { client =>
              client.request(newRequest)
            }
        },
        Method.POST / "2" -> handler { (req: Request) =>
          ZIO.debug("#2: got response, collecting") *>
            req.body.asChunk.map { body =>
              Response.text(body.length.toString)
            }
        },
      ).sandbox.toHttpApp
      val sizes = Chunk(0, 8192, 1024 * 1024)
      sizes.map { size =>
        test(s"with body length $size") {
          for {
            testBytes <- Random.nextBytes(size)
            res <- app.deploy(Request(method = Method.POST, url = URL.root / "1", body = Body.fromChunk(testBytes)))
            str <- res.body.asString
          } yield assertTrue(
            extractStatus(res).isSuccess,
            str == testBytes.length.toString,
          )
        }
      }
    }: _*),
  ) @@ timeout(10 seconds)

  override def spec =
    suite("RequestStreamingServerSpec") {
      suite("app with request streaming") {
        appWithReqStreaming.as(List(requestBodySpec, streamingServerSpec))
      }
    }.provideSome[DynamicServer & Server.Config & Server & Client](Scope.default)
      .provideShared(
        DynamicServer.live,
        ZLayer.succeed(configAppWithRequestStreaming),
        Server.live,
        Client.default,
      ) @@ timeout(30 seconds) @@ diagnose(15.seconds) @@ sequential @@ shrinks(0) @@ withLiveClock

}
