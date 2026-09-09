package zio.http

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

import scala.annotation.experimental

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.ResultType._

/**
 * Response-body caps on every client leg, all sourced from
 * `ClientConfig.maxResponseBodySize` (default 16 MiB, matching the one-shot
 * wire cap).
 *
 * A malicious server must not force unbounded buffering: bodies are accounted
 * incrementally and the request fails fast past the cap — the one-shot
 * [[H2WireClient]] leg (via [[LoomH2ClientDriver]]), the pooled
 * [[PooledLoomH2Client]] leg (lazy streaming bodies), and the JDK
 * [[JavaH2Client]] leg (bounded subscriber instead of `ofByteArray`).
 *
 * Real servers, tiny caps (4 KiB) against a 256 KiB body: H2 legs run against a
 * real [[LoomServer]]; the JDK leg runs against the JDK-built-in HTTP/1.1
 * server (no new dependencies — the JDK client downgrades to `http/1.1` there,
 * which also proves the cap lives on the body handler, not the protocol leg).
 */
@experimental
object ClientResponseCapSpec extends ZIOSpecDefault {

  private val BigBodySize: Int = 256 * 1024
  private val TinyCap: Long    = 4096L

  private val BigBytes: Array[Byte] = Array.fill(BigBodySize)(0x41.toByte)

  private val BigRoutes: Routes[Any] =
    Routes(
      Route(
        RoutePattern.GET,
        handler { (_: Request) =>
          responseAsResult(Response(status = Status.Ok, body = Body.fromArray(BigBytes)))
        },
      ),
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ClientResponseCapSpec")(
      test("one-shot leg fails fast past a tiny cap") {
        withLoomServer { port =>
          ZIO.attemptBlocking {
            val driver  = LoomH2ClientDriver(ClientConfig(maxResponseBodySize = TinyCap))
            val failure =
              try {
                driver.send(Request.get(absUrl(s"http://127.0.0.1:$port/")))
                None
              } catch {
                case failure: ResponseBodyTooLarge => Some(failure)
                case failure: Throwable            => None
              }
            assertTrue(failure.exists(_.maxBytes == TinyCap))
          }
        }
      },
      test("one-shot leg succeeds under the default cap") {
        withLoomServer { port =>
          ZIO.attemptBlocking {
            val driver   = LoomH2ClientDriver(ClientConfig())
            val response = driver.send(Request.get(absUrl(s"http://127.0.0.1:$port/")))
            assertTrue(
              response.status == Status.Ok,
              response.body.toArray.sameElements(BigBytes),
            )
          }
        }
      },
      test("pooled leg fails fast past a tiny cap") {
        withLoomServer { port =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig(maxResponseBodySize = TinyCap))
            try {
              val response = pool.send(Request.get(absUrl(s"http://127.0.0.1:$port/")))
              val failure  =
                try {
                  response.body.toArray
                  None
                } catch {
                  case failure: ResponseBodyTooLarge => Some(failure)
                  case failure: Throwable            => None
                }
              assertTrue(response.status == Status.Ok, failure.exists(_.maxBytes == TinyCap))
            } finally pool.close()
          }
        }
      },
      test("pooled leg succeeds under the default cap") {
        withLoomServer { port =>
          ZIO.attemptBlocking {
            val pool = PooledLoomH2Client(ClientConfig())
            try {
              val response = pool.send(Request.get(absUrl(s"http://127.0.0.1:$port/")))
              assertTrue(
                response.status == Status.Ok,
                response.body.toArray.sameElements(BigBytes),
              )
            } finally pool.close()
          }
        }
      },
      test("JDK leg fails fast past a tiny cap") {
        withJdkServer { port =>
          ZIO.attemptBlocking {
            val client  = JavaH2Client(ClientConfig(maxResponseBodySize = TinyCap))
            val failure =
              try {
                client.send(Request.get(absUrl(s"http://127.0.0.1:$port/")))
                None
              } catch {
                case failure: Throwable => Some(failureToString(failure))
              }
            assertTrue(failure.exists(_.contains("client cap")))
          }
        }
      },
      test("JDK leg succeeds under the default cap") {
        withJdkServer { port =>
          ZIO.attemptBlocking {
            val client   = JavaH2Client(ClientConfig())
            val response = client.send(Request.get(absUrl(s"http://127.0.0.1:$port/")))
            assertTrue(
              response.status == Status.Ok,
              response.body.toArray.sameElements(BigBytes),
            )
          }
        }
      },
      test("default cap is 16 MiB and non-positive caps fail fast") {
        ZIO.attempt {
          val rejectsZero     = rejects(ClientConfig(maxResponseBodySize = 0L))
          val rejectsNegative = rejects(ClientConfig(maxResponseBodySize = -1L))
          assertTrue(
            ClientConfig.DefaultMaxResponseBodySize == 16L * 1024L * 1024L,
            ClientConfig().maxResponseBodySize == ClientConfig.DefaultMaxResponseBodySize,
            JavaH2Client.DefaultMaxResponseBodySize == ClientConfig.DefaultMaxResponseBodySize,
            rejectsZero,
            rejectsNegative,
          )
        }
      },
      test("capped stream counts incrementally and trips exactly past the cap") {
        ZIO.attempt {
          val under     = new PooledLoomH2Client.CappedResponseStream(
            new java.io.ByteArrayInputStream(Array.fill(100)(1.toByte)),
            100L,
          )
          val underRead = under.read(new Array[Byte](100), 0, 100)
          val over      = new PooledLoomH2Client.CappedResponseStream(
            new java.io.ByteArrayInputStream(Array.fill(101)(1.toByte)),
            100L,
          )
          val tripped   =
            try {
              val buf = new Array[Byte](101)
              var got = 0
              var n   = over.read(buf, 0, 101)
              while (n >= 0) { got += n; n = over.read(buf, 0, 101) }
              None
            } catch {
              case failure: ResponseBodyTooLarge => Some(failure.maxBytes)
            }
          assertTrue(underRead == 100, tripped.contains(100L))
        }
      },
    ) @@ sequential

  private def rejects(thunk: => Any): Boolean =
    try {
      thunk
      false
    } catch {
      case _: IllegalArgumentException => true
    }

  /** Unwraps ExecutionException chains so the cap message is observable. */
  private def failureToString(failure: Throwable): String = {
    val builder = new StringBuilder()
    var current = failure
    while (current != null) {
      builder.append(current.toString).append(" <- ")
      current = current.getCause
    }
    builder.toString
  }

  private def absUrl(raw: String): URL =
    URL.parse(raw).fold(err => throw new IllegalArgumentException("Invalid test URL: " + err), identity)

  private def withLoomServer[R](
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          new LoomServer(Connector(bind = BindAddress.localhost(0))).serve(BigRoutes, Context.empty)
        },
      )(handle => ZIO.attemptBlocking(handle.shutdownAndWait()).ignore)
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, value) => value
          case other                      => throw new AssertionError("Expected TCP binding but found: " + other)
        }
        use(port)
      }

  private def withJdkServer[R](
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
          server.createContext(
            "/",
            (exchange: com.sun.net.httpserver.HttpExchange) => {
              exchange.sendResponseHeaders(200, BigBytes.length.toLong)
              val out = exchange.getResponseBody
              try out.write(BigBytes, 0, BigBytes.length)
              finally {
                out.close()
                exchange.close()
              }
            },
          )
          server.setExecutor(null)
          server.start()
          server
        },
      )(server => ZIO.succeed(server.stop(0)))
      .flatMap(server => use(server.getAddress.getPort))
}
