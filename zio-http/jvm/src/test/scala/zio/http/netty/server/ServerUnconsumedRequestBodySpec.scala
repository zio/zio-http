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

import java.io.{IOException, InputStream, OutputStream}
import java.net.{InetSocketAddress, Socket, SocketTimeoutException}
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

  val bodySize: Int = 16 * 1024

  private val readTimeout: Duration = 10.seconds

  // Larger than what the kernel buffers of both ends can hold together, so that a slow client leaves part of it
  // queued in the server when the server decides to close.
  private val largeRejectionSize: Int = 16 * 1024 * 1024

  private val largeRejection: Array[Byte] = Array.fill[Byte](largeRejectionSize)('r'.toByte)

  private def routes(readBody: Request => ZIO[Any, Nothing, Unit]): Routes[Any, Response] =
    Routes(
      Method.POST / "reject" -> handler((request: Request) => readBody(request).as(Response.status(Status.BadRequest))),
      Method.POST / "reject-loudly" -> handler(
        Response(status = Status.BadRequest, body = Body.fromArray(largeRejection)),
      ),
      Method.POST / "echo"          -> handler((request: Request) => Response(body = request.body)),
      Method.GET / "ping"           -> handler(Response.text("pong")),
    )

  private def startServer(
    readBody: Request => ZIO[Any, Nothing, Unit],
    config: Server.Config,
  ): ZIO[Scope, Throwable, Int] =
    for {
      server <- (ZLayer.succeed(config.onAnyOpenPort.enableRequestStreaming) >>> Server.live).build
      port   <- Server.installRoutes(routes(readBody)).provideEnvironment(server)
    } yield port

  private def connect(port: Int, receiveBufferSize: Option[Int]): ZIO[Scope, Throwable, Socket] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        val socket = new Socket()
        // Set before connecting, since it determines the window the client advertises.
        receiveBufferSize.foreach(socket.setReceiveBufferSize)
        socket.setTcpNoDelay(true)
        socket.setSoTimeout(readTimeout.toMillis.toInt)
        socket.connect(new InetSocketAddress("localhost", port))
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

  private def readResponse(socket: Socket, pauseBetweenReads: Option[Duration]): Task[Outcome] =
    ZIO.attemptBlocking {
      val in: InputStream = socket.getInputStream

      @tailrec
      def readHead(read: Chunk[Byte]): Option[String] =
        in.read() match {
          case -1 if read.isEmpty => None
          case -1   => throw new IllegalStateException("Connection closed half way through a response head")
          case byte =>
            val readSoFar = read :+ byte.toByte
            if (readSoFar.endsWith(Chunk[Byte]('\r', '\n', '\r', '\n')))
              Some(new String(readSoFar.toArray, StandardCharsets.US_ASCII))
            else readHead(readSoFar)
        }

      def readBody(contentLength: Int): Int = {
        val buffer = new Array[Byte](16 * 1024)
        var read   = 0
        var eof    = false
        while (read < contentLength && !eof) {
          val n =
            try in.read(buffer, 0, math.min(buffer.length, contentLength - read))
            catch {
              case timeout: SocketTimeoutException => throw timeout
              case _: IOException                  => -1
            }
          if (n < 0) eof = true else read += n
          pauseBetweenReads.foreach(pause => Thread.sleep(pause.toMillis))
        }
        read
      }

      try {
        readHead(Chunk.empty) match {
          case None       => Outcome.Closed
          case Some(head) =>
            val lines         = head.linesIterator.toList
            val contentLength = lines.collectFirst {
              case line if line.toLowerCase.startsWith("content-length:") =>
                line.drop("content-length:".length).trim.toInt
            }.getOrElse(0)
            Outcome.Answered(lines.head, contentLength, readBody(contentLength))
        }
      } catch {
        case _: SocketTimeoutException => Outcome.Silent
        case _: IOException            => Outcome.Closed
      }
    }

  private def scenario(
    readBody: Request => ZIO[Any, Nothing, Unit],
    config: Server.Config,
    path: String,
    client: ClientBehaviour,
  ): ZIO[Scope, Throwable, Exchange] = {
    def sendRequestBody(socket: Socket): ZIO[Any, Nothing, Boolean] =
      // A server that gives up on the connection may reset it while the body is still being sent, or stop reading
      // it and leave the write blocked for good, which is why the write is not waited on beyond a point.
      sendBody(socket, client.requestBodySize).disconnect.timeout(readTimeout).map(_.isDefined).orElseSucceed(false)

    for {
      port     <- startServer(readBody, config)
      socket   <- connect(port, client.receiveBufferSize)
      _        <- sendHead(socket, s"POST $path HTTP/1.1", client.requestBodySize)
      // The body lands in a read of its own, which is when reading stops.
      _        <- ZIO.sleep(300.millis)
      sending  <- sendRequestBody(socket).fork
      _        <- sending.join.when(client.sendsBodyBeforeReading)
      response <- readResponse(socket, client.pauseBetweenReads)
      bodySent <- sending.join
      _        <- sendHead(socket, "GET /ping HTTP/1.1", contentLength = 0).ignore
      reused   <- readResponse(socket, pauseBetweenReads = None)
    } yield Exchange(bodySent, response, reused)
  }

  private val overBudget: Server.Config = Server.Config.default.maxDiscardedRequestBodySize(1024L)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerUnconsumedRequestBody")(
      test("a handler that never touches the body leaves the connection usable") {
        for {
          exchange <- scenario(_ => ZIO.unit, Server.Config.default, "/reject", ClientBehaviour.default)
        } yield assertTrue(exchange.response.isAnswered("400"), exchange.reused.isAnswered("200"))
      },
      test("a handler that consumes only part of the body leaves the connection usable") {
        for {
          exchange <- scenario(
            _.body.asStream.take(1).runDrain.orDie,
            Server.Config.default,
            "/reject",
            ClientBehaviour.default,
          )
        } yield assertTrue(exchange.response.isAnswered("400"), exchange.reused.isAnswered("200"))
      },
      test("a handler that consumes the whole body leaves the connection usable") {
        for {
          exchange <- scenario(
            _.body.asStream.runDrain.orDie,
            Server.Config.default,
            "/reject",
            ClientBehaviour.default,
          )
        } yield assertTrue(exchange.response.isAnswered("400"), exchange.reused.isAnswered("200"))
      },
      test("a response that streams the request body back is written in full before anything is discarded") {
        for {
          exchange <- scenario(_ => ZIO.unit, Server.Config.default, "/echo", ClientBehaviour.default)
        } yield assertTrue(
          exchange.response == Outcome.Answered("HTTP/1.1 200 OK", bodySize, bodySize),
          exchange.reused.isAnswered("200"),
        )
      },
      suite("a body too large to discard is answered in full, and then the connection is closed")(
        test("for a client that has sent the body before reading the response") {
          for {
            exchange <- scenario(_ => ZIO.unit, overBudget, "/reject", ClientBehaviour.default)
          } yield assertTrue(
            exchange.response.isAnswered("400"),
            // Closed, rather than left open and silently swallowing the next request.
            exchange.reused == Outcome.Closed,
          )
        },
        test("for a client that sends a large body in full before it reads a large response") {
          for {
            exchange <- scenario(_ => ZIO.unit, overBudget, "/reject-loudly", ClientBehaviour.slow)
          } yield assertTrue(
            // Not left blocked: the server reads on while it waits for the client to take in the rejection.
            exchange.bodySent,
            exchange.response.isAnswered("400"),
            exchange.reused == Outcome.Closed,
          )
        },
        test("for a client that reads a large response slowly while it is still sending") {
          for {
            exchange <- scenario(
              _ => ZIO.unit,
              overBudget,
              "/reject-loudly",
              ClientBehaviour.slow.copy(sendsBodyBeforeReading = false, pauseBetweenReads = Some(1.milli)),
            )
          } yield assertTrue(exchange.response.isAnswered("400"), exchange.reused == Outcome.Closed)
        },
      ),
    ) @@ withLiveClock @@ sequential @@ timeout(2.minutes)
}

/** What became of asking the connection for a response. */
private sealed trait Outcome {

  /** Whether this is a response with the given status, that arrived in full. */
  def isAnswered(status: String): Boolean =
    this match {
      case answered: Outcome.Answered => answered.statusLine.startsWith(s"HTTP/1.1 $status") && answered.isComplete
      case _                          => false
    }
}

private object Outcome {

  /** A response arrived; `bodyLength` is how much of its body did. */
  final case class Answered(statusLine: String, contentLength: Int, bodyLength: Int) extends Outcome {
    def isComplete: Boolean = bodyLength == contentLength
  }

  /** The server closed the connection, cleanly or by resetting it. */
  case object Closed extends Outcome

  /**
   * Nothing arrived: the connection is open, but the request on it went
   * unanswered.
   */
  case object Silent extends Outcome
}

/**
 * Whether the client got its request body out, what came of the request, and of
 * reusing that same connection afterwards.
 */
private final case class Exchange(bodySent: Boolean, response: Outcome, reused: Outcome)

/**
 * How the client goes about sending a request body and reading the response to
 * it.
 */
private final case class ClientBehaviour(
  receiveBufferSize: Option[Int],
  requestBodySize: Int,
  sendsBodyBeforeReading: Boolean,
  pauseBetweenReads: Option[Duration],
)

private object ClientBehaviour {
  val default: ClientBehaviour =
    ClientBehaviour(
      receiveBufferSize = None,
      requestBodySize = ServerUnconsumedRequestBodySpec.bodySize,
      sendsBodyBeforeReading = true,
      pauseBetweenReads = None,
    )

  // A small window, and a body more than the kernel buffers hold, so that the client is still sending while the
  // rejection is on its way, and part of the rejection is still queued in the server when it decides to close.
  val slow: ClientBehaviour =
    ClientBehaviour(
      receiveBufferSize = Some(16 * 1024),
      requestBodySize = 4 * 1024 * 1024,
      sendsBodyBeforeReading = true,
      pauseBetweenReads = None,
    )
}
