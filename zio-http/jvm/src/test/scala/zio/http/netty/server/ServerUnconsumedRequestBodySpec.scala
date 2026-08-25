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

package zio.http.netty.server

import java.io.{InputStream, OutputStream}
import java.net.Socket
import java.nio.charset.StandardCharsets

import scala.annotation.tailrec

import zio._
import zio.test.TestAspect._
import zio.test._

import zio.http._

/**
 * A response that is written without the request body having been read leaves
 * the connection unreadable unless the server does something about it.
 *
 * With request streaming, reading is driven by the consumer of the body: the
 * channel is put in autoRead = false and the next read is only issued when the
 * next chunk is asked for. A handler that answers without consuming the body -
 * one that rejects the request before decoding it, an unmatched route, or a
 * handler that simply ignores the body - therefore stops the connection from
 * ever being read again, and the next request the client sends over that
 * connection is silently dropped.
 *
 * The body has to arrive in a later read than the headers for this to show,
 * which is why the requests here are written in two parts over a plain socket
 * rather than through a client: a client is free to decide how to frame and
 * whether to reuse a connection, and both are exactly what is being tested.
 */
object ServerUnconsumedRequestBodySpec extends ZIOHttpSpec {

  private val bodySize: Int = 16 * 1024

  private val readTimeout: Duration = 10.seconds

  private def routes(readBody: Request => ZIO[Any, Nothing, Unit]): Routes[Any, Response] =
    Routes(
      Method.POST / "reject" -> handler((request: Request) => readBody(request).as(Response.status(Status.BadRequest))),
      Method.GET / "ping"    -> handler(Response.text("pong")),
    )

  private def startServer(
    readBody: Request => ZIO[Any, Nothing, Unit],
    config: Server.Config,
  ): ZIO[Scope, Throwable, Int] =
    for {
      server <- (ZLayer.succeed(config.onAnyOpenPort.enableRequestStreaming) >>> Server.live).build
      port   <- Server.installRoutes(routes(readBody)).provideEnvironment(server)
    } yield port

  private def connect(port: Int): ZIO[Scope, Throwable, Socket] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        val socket = new Socket("localhost", port)
        socket.setTcpNoDelay(true)
        socket.setSoTimeout(readTimeout.toMillis.toInt)
        socket
      },
    )(socket => ZIO.attemptBlocking(socket.close()).orDie)

  private def sendHead(socket: Socket, requestLine: String, contentLength: Int): Task[Unit] =
    ZIO.attemptBlocking {
      val out: OutputStream = socket.getOutputStream
      out.write(
        s"$requestLine\r\nHost: localhost\r\nContent-Length: $contentLength\r\n\r\n".getBytes(StandardCharsets.US_ASCII),
      )
      out.flush()
    }

  private def sendBody(socket: Socket, size: Int): Task[Unit] =
    ZIO.attemptBlocking {
      val out: OutputStream = socket.getOutputStream
      out.write(Array.fill[Byte](size)('x'.toByte))
      out.flush()
    }

  /** Reads the status line of the next response, or fails if none arrives. */
  private def readStatusLine(socket: Socket): Task[String] =
    ZIO.attemptBlocking {
      val in: InputStream = socket.getInputStream

      @tailrec
      def readHeaders(read: Chunk[Byte]): String =
        in.read() match {
          case -1   => throw new IllegalStateException("Connection closed before a response was received")
          case byte =>
            val readSoFar = read :+ byte.toByte
            if (readSoFar.endsWith(Chunk[Byte]('\r', '\n', '\r', '\n')))
              new String(readSoFar.toArray, StandardCharsets.US_ASCII).linesIterator.next()
            else readHeaders(readSoFar)
        }

      readHeaders(Chunk.empty)
    }

  private final case class Exchange(rejected: String, reused: String)

  private def scenario(
    readBody: Request => ZIO[Any, Nothing, Unit],
    config: Server.Config = Server.Config.default,
  ): ZIO[Scope, Throwable, Exchange] =
    for {
      port     <- startServer(readBody, config)
      socket   <- connect(port)
      _        <- sendHead(socket, "POST /reject HTTP/1.1", bodySize)
      // The body lands in a read of its own, which is when reading stops.
      _        <- ZIO.sleep(300.millis)
      _        <- sendBody(socket, bodySize)
      rejected <- readStatusLine(socket)
      _        <- sendHead(socket, "GET /ping HTTP/1.1", contentLength = 0)
      reused   <- readStatusLine(socket)
    } yield Exchange(rejected, reused)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerUnconsumedRequestBody")(
      test("a handler that never touches the body leaves the connection usable") {
        for {
          exchange <- scenario(_ => ZIO.unit)
        } yield assertTrue(exchange.rejected.startsWith("HTTP/1.1 400"), exchange.reused.startsWith("HTTP/1.1 200"))
      },
      test("a handler that consumes only part of the body leaves the connection usable") {
        for {
          exchange <- scenario(_.body.asStream.take(1).runDrain.orDie)
        } yield assertTrue(exchange.rejected.startsWith("HTTP/1.1 400"), exchange.reused.startsWith("HTTP/1.1 200"))
      },
      test("a handler that consumes the whole body leaves the connection usable") {
        for {
          exchange <- scenario(_.body.asStream.runDrain.orDie)
        } yield assertTrue(exchange.rejected.startsWith("HTTP/1.1 400"), exchange.reused.startsWith("HTTP/1.1 200"))
      },
      test("a body too large to discard closes the connection instead of leaving it unreadable") {
        for {
          result <- scenario(
            _ => ZIO.unit,
            Server.Config.default.maxDiscardedRequestBodySize(bodySize.toLong / 2),
          ).either
        } yield assertTrue(result.isLeft)
      },
    ) @@ withLiveClock @@ sequential @@ timeout(2.minutes)
}
