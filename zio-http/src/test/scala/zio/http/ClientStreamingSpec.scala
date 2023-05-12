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
import zio.test.TestAspect.{nonFlaky, withLiveClock}
import zio.test.{Spec, TestEnvironment, assertTrue}

import zio.stream.{ZStream, ZStreamAspect}

import zio.http.Server.RequestStreaming
import zio.http.internal.HttpRunnableSpec
import zio.http.netty.NettyConfig
import zio.http.netty.NettyConfig.LeakDetectionLevel

object ClientStreamingSpec extends HttpRunnableSpec {

  val app = Http
    .collectZIO[Request] {
      case Method.GET -> Root / "simple-get"            =>
        ZIO.succeed(Response.text("simple response"))
      case Method.GET -> Root / "streaming-get"         =>
        ZIO.succeed(
          Response(body =
            Body.fromStream(ZStream.fromIterable("streaming response".getBytes) @@ ZStreamAspect.rechunk(3)),
          ),
        )
      case req @ Method.POST -> Root / "simple-post"    =>
        req.ignoreBody.as(Response.ok)
      case req @ Method.POST -> Root / "streaming-echo" =>
        ZIO.succeed(Response(body = Body.fromStream(req.body.asStream)))
    }
    .withDefaultErrorResponse

  // TODO: test failure cases

  private def tests(streamingServer: Boolean): Seq[Spec[Client, Throwable]] =
    Seq(
      test("simple get") {
        for {
          port     <- server(streamingServer)
          client   <- ZIO.service[Client]
          response <- client.request(
            Version.Http_1_1,
            Method.GET,
            URL.decode(s"http://localhost:$port/simple-get").toOption.get,
            Headers.empty,
            Body.empty,
            None,
          )
          body     <- response.body.asString
        } yield assertTrue(response.status == Status.Ok, body == "simple response")
      },
      test("streaming get") {
        for {
          port     <- server(streamingServer)
          client   <- ZIO.service[Client]
          response <- client.request(
            Version.Http_1_1,
            Method.GET,
            URL.decode(s"http://localhost:$port/streaming-get").toOption.get,
            Headers.empty,
            Body.empty,
            None,
          )
          body     <- response.body.asStream.chunks.map(chunk => new String(chunk.toArray)).runCollect
        } yield assertTrue(
          response.status == Status.Ok,
          body == Chunk(
            "str",
            "eam",
            "ing",
            " re",
            "spo",
            "nse",
            "",
          ),
        )
      },
      test("simple post") {
        for {
          port     <- server(streamingServer)
          client   <- ZIO.service[Client]
          response <- client
            .request(
              Version.Http_1_1,
              Method.POST,
              URL.decode(s"http://localhost:$port/simple-post").toOption.get,
              Headers.empty,
              Body.fromStream(
                (ZStream.fromIterable("streaming request".getBytes) @@ ZStreamAspect.rechunk(3))
                  .schedule(Schedule.fixed(10.millis)),
              ),
              None,
            )
        } yield assertTrue(response.status == Status.Ok)
      },
      test("echo") {
        for {
          port     <- server(streamingServer)
          client   <- ZIO.service[Client]
          response <- client
            .request(
              Version.Http_1_1,
              Method.POST,
              URL.decode(s"http://localhost:$port/streaming-echo").toOption.get,
              Headers.empty,
              Body.fromStream(
                ZStream.fromIterable("streaming request".getBytes) @@ ZStreamAspect.rechunk(3),
              ),
              None,
            )
          body     <- response.body.asStream.chunks.map(chunk => new String(chunk.toArray)).runCollect
          expectedBody =
            if (streamingServer)
              Chunk(
                "str",
                "eam",
                "ing",
                " re",
                "que",
                "st",
                "",
              )
            else
              Chunk("streaming request", "")
        } yield assertTrue(
          response.status == Status.Ok,
          body == expectedBody,
        )
      },
    )

  private def streamingOnlyTests =
    Seq(
      test("echo with sync point") {
        for {
          port     <- server(streaming = true)
          client   <- ZIO.service[Client]
          sync     <- Promise.make[Nothing, Unit]
          response <- client
            .request(
              Version.Http_1_1,
              Method.POST,
              URL.decode(s"http://localhost:$port/streaming-echo").toOption.get,
              Headers.empty,
              Body.fromStream(
                (ZStream.fromIterable("streaming request".getBytes) @@ ZStreamAspect.rechunk(3)).chunks.tap { chunk =>
                  if (chunk == Chunk.fromArray("que".getBytes))
                    sync.await
                  else
                    ZIO.unit
                }.flattenChunks,
              ),
              None,
            )
          body     <- response.body.asStream.chunks
            .map(chunk => new String(chunk.toArray))
            .tap { chunk =>
              if (chunk == "eam") sync.succeed(()) else ZIO.unit
            }
            .runCollect
          expectedBody =
            Chunk(
              "str",
              "eam",
              "ing",
              " re",
              "que",
              "st",
              "",
            )
        } yield assertTrue(
          response.status == Status.Ok,
          body == expectedBody,
        )
      } @@ nonFlaky,
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Client streaming")(
      suite("streaming server")(
        tests(streamingServer = true) ++
          streamingOnlyTests: _*,
      ),
      suite("non-streaming server")(
        tests(streamingServer = false): _*,
      ),
    ).provide(
      Client.default,
    ) @@ withLiveClock

  private def server(streaming: Boolean): ZIO[Any, Throwable, Int] =
    for {
      portPromise <- Promise.make[Throwable, Int]
      _           <- Server
        .install(app)
        .intoPromise(portPromise)
        .zipRight(ZIO.never)
        .provide(
          ZLayer.succeed(NettyConfig.default.leakDetection(LeakDetectionLevel.PARANOID)),
          ZLayer.succeed(
            Server.Config.default.onAnyOpenPort
              .withRequestStreaming(if (streaming) RequestStreaming.Enabled else RequestStreaming.Disabled(1024)),
          ),
          Server.customized,
        )
        .fork
      port        <- portPromise.await
    } yield port
}
