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
import zio.test.Assertion.{equalTo, fails, hasMessage}
import zio.test.TestAspect._
import zio.test._

import zio.stream.{ZStream, ZStreamAspect}

import zio.http.Server.RequestStreaming
import zio.http.forms.Fixtures.formField
import zio.http.internal.HttpRunnableSpec
import zio.http.netty.NettyConfig
import zio.http.netty.NettyConfig.LeakDetectionLevel

object ClientStreamingSpec extends HttpRunnableSpec {
  def extractStatus(response: Response): Status = response.status

  val app = Routes(
    Method.GET / "simple-get"      ->
      handler(Response.text("simple response")),
    Method.GET / "streaming-get"   ->
      handler(
        Response(body = Body.fromStream(ZStream.fromIterable("streaming response".getBytes).rechunk(3))),
      ),
    Method.POST / "simple-post"    -> handler((req: Request) => req.ignoreBody.as(Response.ok)),
    Method.POST / "streaming-echo" -> handler((req: Request) => Response(body = Body.fromStream(req.body.asStream))),
    Method.POST / "form"           -> handler((req: Request) =>
      req.body.asMultipartFormStream.flatMap { form =>
        form.collectAll.flatMap { inMemoryForm =>
          Body.fromMultipartFormUUID(inMemoryForm).map { body =>
            Response(body = body)
          }
        }
      },
    ),
  ).sandbox.toHttpApp

  // TODO: test failure cases

  private def tests(streamingServer: Boolean): Seq[Spec[Client with Scope, Throwable]] =
    Seq(
      test("simple get") {
        for {
          port     <- server(streamingServer)
          client   <- ZIO.service[Client]
          response <- client.request(
            Request.get(URL.decode(s"http://localhost:$port/simple-get").toOption.get),
          )
          body     <- response.body.asString
        } yield assertTrue(extractStatus(response) == Status.Ok, body == "simple response")
      },
      test("streaming get") {
        for {
          port     <- server(streamingServer)
          client   <- ZIO.service[Client]
          response <- client.request(
            Request.get(URL.decode(s"http://localhost:$port/streaming-get").toOption.get),
          )
          body     <- response.body.asStream.chunks.map(chunk => new String(chunk.toArray)).runCollect
        } yield assertTrue(
          extractStatus(response) == Status.Ok,
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
              Request.post(
                URL.decode(s"http://localhost:$port/simple-post").toOption.get,
                Body.fromStream(
                  (ZStream.fromIterable("streaming request".getBytes) @@ ZStreamAspect.rechunk(3))
                    .schedule(Schedule.fixed(10.millis)),
                ),
              ),
            )
        } yield assertTrue(extractStatus(response) == Status.Ok)
      },
      test("echo") {
        for {
          port     <- server(streamingServer)
          client   <- ZIO.service[Client]
          response <- client
            .request(
              Request.post(
                URL.decode(s"http://localhost:$port/streaming-echo").toOption.get,
                Body.fromStream(
                  ZStream.fromIterable("streaming request".getBytes) @@ ZStreamAspect.rechunk(3),
                ),
              ),
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
          extractStatus(response) == Status.Ok,
          body == expectedBody,
        )
      },
      test("decoding random form") {
        for {
          port   <- server(streamingServer)
          client <- ZIO.service[Client]

          result <- check(Gen.chunkOfBounded(2, 8)(formField)) { fields =>
            for {
              boundary <- Boundary.randomUUID
              response <- client
                .request(
                  Request
                    .post(
                      URL.decode(s"http://localhost:$port/form").toOption.get,
                      Body.fromMultipartForm(Form(fields.map(_._1): _*), boundary),
                    )
                    .addHeaders(Headers(Header.ContentType(MediaType.multipart.`form-data`, Some(boundary)))),
                )
                .timeoutFail(new RuntimeException("Client request timed out"))(20.seconds)
              form     <- response.body.asMultipartForm

              normalizedIn  <- ZIO.foreach(fields.map(_._1)) { field =>
                field.asChunk.map(field.name -> _)
              }
              normalizedOut <- ZIO.foreach(form.formData) { field =>
                field.asChunk.map(field.name -> _)
              }
            } yield assertTrue(
              normalizedIn == normalizedOut,
            )
          }
        } yield result
      } @@ samples(50),
      test("decoding random pre-encoded form") {
        for {
          port   <- server(streamingServer)
          client <- ZIO.service[Client]
          result <- check(Gen.chunkOfBounded(2, 8)(formField)) { fields =>
            for {
              boundary <- Boundary.randomUUID
              stream = Form(fields.map(_._1): _*).multipartBytes(boundary)
              bytes    <- stream.runCollect
              response <- client.disableStreaming
                .request(
                  Request
                    .post(
                      URL.decode(s"http://localhost:$port/form").toOption.get,
                      Body.fromChunk(bytes),
                    )
                    .addHeaders(Headers(Header.ContentType(MediaType.multipart.`form-data`, Some(boundary)))),
                )
                .timeoutFail(new RuntimeException("Client request timed out"))(20.seconds)
              form     <- response.body.asMultipartForm

              normalizedIn  <- ZIO.foreach(fields.map(_._1)) { field =>
                field.asChunk.map(field.name -> _)
              }
              normalizedOut <- ZIO.foreach(form.formData) { field =>
                field.asChunk.map(field.name -> _)
              }
            } yield assertTrue(
              normalizedIn == normalizedOut,
            )
          }
        } yield result
      } @@ samples(50),
      test("decoding large form with random chunk and buffer sizes") {
        val N = 1024 * 1024
        for {
          port   <- server(streamingServer)
          client <- ZIO.service[Client]
          result <- check(Gen.int(1, N)) { chunkSize =>
            (for {
              bytes <- Random.nextBytes(N)
              form = Form(
                Chunk(
                  FormField.Simple("foo", "bar"),
                  FormField.Binary("file", bytes, MediaType.image.png),
                ),
              )
              boundary <- Boundary.randomUUID
              stream = form.multipartBytes(boundary).rechunk(chunkSize)
              response  <- client
                .request(
                  Request
                    .post(
                      URL.decode(s"http://localhost:$port/form").toOption.get,
                      Body.fromStream(stream),
                    )
                    .addHeaders(Headers(Header.ContentType(MediaType.multipart.`form-data`, Some(boundary)))),
                )
                .timeoutFail(new RuntimeException("Client request timed out"))(20.seconds)
              collected <- response.body.asMultipartForm
            } yield assertTrue(
              collected.map.contains("file"),
              collected.map.contains("foo"),
              collected.get("file").get.asInstanceOf[FormField.Binary].data == bytes,
            )).tapErrorCause(cause => ZIO.debug(cause.prettyPrint))
          }
        } yield result
      } @@ samples(20),
      test("failed stream") {
        for {
          port     <- server(streamingServer)
          client   <- ZIO.service[Client]
          response <- client
            .request(
              Request.post(
                URL.decode(s"http://localhost:$port/simple-post").toOption.get,
                Body.fromStream(ZStream.fail(new RuntimeException("Some error"))),
              ),
            )
            .exit
        } yield assert(response)(fails(hasMessage(equalTo("Some error"))))
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
              Request.post(
                URL.decode(s"http://localhost:$port/streaming-echo").toOption.get,
                Body.fromStream(
                  (ZStream.fromIterable("streaming request".getBytes) @@ ZStreamAspect.rechunk(3)).chunks.tap { chunk =>
                    if (chunk == Chunk.fromArray("que".getBytes))
                      sync.await
                    else
                      ZIO.unit
                  }.flattenChunks,
                ),
              ),
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
          extractStatus(response) == Status.Ok,
          body == expectedBody,
        )
      } @@ nonFlaky,
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ClientStreamingSpec")(
      suite("streaming server")(
        tests(streamingServer = true) ++
          streamingOnlyTests: _*,
      ),
      suite("non-streaming server")(
        tests(streamingServer = false): _*,
      ),
    ).provide(
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.default),
      ZLayer.succeed(Client.Config.default.connectionTimeout(100.seconds).idleTimeout(100.seconds)),
      Client.live,
      Scope.default,
    ) @@ withLiveClock @@ sequential

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
              .requestStreaming(
                if (streaming) RequestStreaming.Enabled else RequestStreaming.Disabled(2 * 1024 * 1024),
              )
              .idleTimeout(100.seconds),
          ),
          Server.customized,
        )
        .fork
      port        <- portPromise.await
    } yield port
}
