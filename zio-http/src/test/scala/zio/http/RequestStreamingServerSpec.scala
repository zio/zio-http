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
        .toHttp
      val res     = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString(content))
      assertZIO(res)(equalTo(size.toString))
    },
    test("multiple body read") {
      val app = Http.collectZIO[Request] { case req =>
        for {
          _ <- req.body.asChunk
          _ <- req.body.asChunk
        } yield Response.ok
      }
      val res = app.deploy.status.run()
      assertZIO(res)(equalTo(Status.InternalServerError))
    },
    suite("streaming request passed to client")({
      val app   = Http
        .collectHandler[Request] {
          case req @ Method.POST -> Root / "1" =>
            Handler.fromZIO {
              val host       = req.headers.get(Header.Host).get
              val newRequest =
                req.copy(url = req.url.withPath("/2").withHost(host.hostAddress).withPort(host.port.getOrElse(80)))
              ZIO.debug(s"#1: got response, forwarding") *>
                ZIO.serviceWithZIO[Client] { client =>
                  client.request(newRequest)
                }
            }
          case req @ Method.POST -> Root / "2" =>
            Handler.fromZIO {
              ZIO.debug("#2: got response, collecting") *>
                req.body.asChunk.map { body =>
                  Response.text(body.length.toString)
                }
            }
        }
        .catchAllCauseZIO(cause =>
          ZIO
            .debug(s"got error: $cause")
            .as(Response.fromHttpError(HttpError.InternalServerError(cause = Some(FiberFailure(cause))))),
        )
      val sizes = Chunk(0, 8192, 1024 * 1024)
      sizes.map { size =>
        test(s"with body length $size") {
          for {
            testBytes <- Random.nextBytes(size)
            res       <- app.deploy.run(method = Method.POST, path = Root / "1", body = Body.fromChunk(testBytes))
            str       <- res.body.asString
          } yield assertTrue(
            res.status.isSuccess,
            str == testBytes.length.toString,
          )
        }
      }
    }: _*),
  ) @@ timeout(10 seconds)

  override def spec =
    suite("RequestStreamingServerSpec") {
      suite("app with request streaming") {
        ZIO.scoped(appWithReqStreaming.as(List(requestBodySpec, streamingServerSpec)))
      }
    }.provideSomeShared[Scope](
      DynamicServer.live,
      ZLayer.succeed(configAppWithRequestStreaming),
      Server.live,
      Client.default,
    ) @@ timeout(30 seconds) @@ diagnose(15.seconds) @@ sequential @@ shrinks(0) @@ withLiveClock

}
