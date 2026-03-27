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
import zio.http.Server.RequestStreaming
import zio.http.netty.NettyConfig
import zio.stream.ZStream
import zio.test.TestAspect._
import zio.test._

import java.util.UUID

/**
 * Regression test for request streaming + concurrent load.
 *
 * 3.9.0 introduced autoRead toggling in ServerInboundHandler.attemptFullWrite
 * that races with AsyncBodyReader's autoRead management when requestStreaming
 * is enabled. Under concurrent load this causes channels to get stuck in an
 * unreadable state, making the server stop responding to some requests.
 */
object RequestStreamingConcurrencySpec extends ZIOSpecDefault {

  private val Parallelism = 100
  private val OpsPerFiber = 20
  private val PayloadSize = 4096

  // In-memory store: UUID -> bytes
  private val store = new java.util.concurrent.ConcurrentHashMap[UUID, Array[Byte]]()

  private val routes = Routes(
    // POST /store — consume streamed body, store it, return the id
    Method.POST / "store" -> handler { (req: Request) =>
      for {
        bytes <- req.body.asChunk
        id = UUID.randomUUID()
        _  = store.put(id, bytes.toArray)
      } yield Response(
        Status.Created,
        Headers(Header.Custom("X-Id", id.toString)),
      )
    },

    // GET /fetch/:id — return stored bytes as a streamed response
    Method.GET / "fetch" / string("id") -> handler { (id: String, _: Request) =>
      val uuid  = UUID.fromString(id)
      val bytes = store.get(uuid)
      if (bytes eq null) ZIO.succeed(Response.notFound)
      else ZIO.succeed(Response(body = Body.fromStreamChunked(ZStream.fromChunk(Chunk.fromArray(bytes)))))
    },
  ).sandbox

  private def server: ZIO[Any, Throwable, Int] =
    for {
      portPromise <- Promise.make[Throwable, Int]
      _           <- Server
        .installRoutes(routes)
        .intoPromise(portPromise)
        .zipRight(ZIO.never)
        .provide(
          ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
          ZLayer.succeed(
            Server.Config.default.onAnyOpenPort
              .requestStreaming(RequestStreaming.Enabled)
              .idleTimeout(100.seconds),
          ),
          Server.customized,
        )
        .fork
      port        <- portPromise.await
    } yield port

  private def storePayload(port: Int, payload: Chunk[Byte]): RIO[Scope with Client, UUID] =
    for {
      resp <- Client.streaming(
        Request.post(
          URL.decode(s"http://localhost:$port/store").toOption.get,
          Body.fromChunk(payload),
        ),
      )
      _    <- ZIO.when(resp.status != Status.Created)(
        resp.body.asString.flatMap(body => ZIO.fail(new RuntimeException(s"Store failed: ${resp.status} $body"))),
      )
      id   <- ZIO
        .fromOption(resp.headers.get("X-Id"))
        .mapBoth(
          _ => new RuntimeException("Missing X-Id header"),
          h => UUID.fromString(h),
        )
    } yield id

  private def fetchPayload(port: Int, id: UUID): RIO[Scope with Client, Chunk[Byte]] =
    for {
      resp  <- Client.streaming(
        Request.get(URL.decode(s"http://localhost:$port/fetch/$id").toOption.get),
      )
      _     <- ZIO.when(resp.status != Status.Ok)(
        ZIO.fail(new RuntimeException(s"Fetch failed: ${resp.status}")),
      )
      bytes <- resp.body.asChunk
    } yield bytes

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("RequestStreamingConcurrencySpec")(
      test(s"${Parallelism}x$OpsPerFiber concurrent store+fetch with requestStreaming(Enabled) should not hang") {
        for {
          port    <- server
          payload <- Random.nextBytes(PayloadSize)
          results <- ZIO
            .foreachPar((1 to Parallelism).toList) { _ =>
              ZIO.foreach((1 to OpsPerFiber).toList) { _ =>
                for {
                  id    <- storePayload(port, payload)
                  bytes <- fetchPayload(port, id)
                } yield (payload.length, bytes.length)
              }
            }
            .timeoutFail(new RuntimeException("TIMEOUT"))(120.seconds)
          res = results.flatten
        } yield assertTrue(
          res.size == Parallelism * OpsPerFiber,
          res.forall { case (expected, actual) => expected == actual },
        )
      },
    )
      .provideSome[Client](Scope.default)
      .provideShared(
        DnsResolver.default,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
        ZLayer.succeed(Client.Config.default.connectionTimeout(100.seconds).idleTimeout(100.seconds)),
        Client.live,
      ) @@ withLiveClock @@ sequential @@ timeout(180.seconds) @@
      TestAspect.after(ZIO.succeed(store.clear()))
}
