package zio.http.h2

import java.io.InputStream
import java.net.{InetSocketAddress, Socket}
import java.nio.charset.StandardCharsets

import scala.annotation.experimental
import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2Frame.{Data, GoAway, Headers, Settings, WindowUpdate}
import zio.http.h2.hpack.{HeaderField, Hpack}
import zio.http.{BindAddress, Body, BoundAddress, Connector, DefectHandler, Halt, Handler, Method, Middleware, Request, Response, Route, Routes, ServerHandle, Status, handler}
import zio.http.ResultType._

@experimental
object H2IntegrationSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2IntegrationSpec")(
      test("basic GET returns 200 over real H2C") {
        withServer(
          Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok))),
        ) { port =>
          withClient(port) { client =>
            for {
              response <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/", body = Chunk.empty, streamId = 1))
            } yield assertTrue(response.status == 200, response.body.isEmpty)
          }
        }
      },
      test("POST with body echoes request payload") {
        val routes = Routes(
          Route(
            RoutePattern.POST,
            handler { (req: Request) =>
              responseAsResult(Response(status = Status.Ok, body = req.body))
            },
          ),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            val body = Chunk.fromArray("loom-body".getBytes(StandardCharsets.UTF_8))
            for {
              response <- ZIO.attemptBlocking(client.roundTrip(method = "POST", path = "/", body = body, streamId = 1))
            } yield assertTrue(response.status == 200, response.body == body)
          }
        }
      },
      test("HEAD request strips response body and preserves content-length") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            Handler.succeed(Response.text("head-body").addHeader(zio.http.Header.ContentLength(9L))),
          ),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              response <- ZIO.attemptBlocking(client.roundTrip(method = "HEAD", path = "/", body = Chunk.empty, streamId = 1))
            } yield assertTrue(
              response.status == 200,
              response.body.isEmpty,
              response.headerValue("content-length").contains("9"),
            )
          }
        }
      },
      test("large body spans multiple DATA frames and echoes correctly") {
        val routes = Routes(
          Route(
            RoutePattern.POST,
            handler { (req: Request) =>
              responseAsResult(Response(status = Status.Ok, body = req.body))
            },
          ),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            val body = Chunk.fromArray(new Array[Byte](20000))
            for {
              response <- ZIO.attemptBlocking(client.roundTrip(method = "POST", path = "/", body = body, streamId = 1))
            } yield assertTrue(response.status == 200, response.body == body)
          }
        }
      },
      test("empty body POST returns 200 with empty body") {
        val routes = Routes(
          Route(
            RoutePattern.POST,
            handler { (req: Request) =>
              responseAsResult(Response(status = Status.Ok, body = req.body))
            },
          ),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              response <- ZIO.attemptBlocking(client.roundTrip(method = "POST", path = "/", body = Chunk.empty, streamId = 1))
            } yield assertTrue(response.status == 200, response.body.isEmpty)
          }
        }
      },
      test("response headers are passed through to the client") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            Handler.succeed(Response.ok.addHeader(zio.http.Header.Custom("x-custom", "foo"))),
          ),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              response <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/", body = Chunk.empty, streamId = 1))
            } yield assertTrue(
              response.status == 200,
              response.headerValue("x-custom").contains("foo"),
            )
          }
        }
      },
      test("content-length is auto-set on non-empty responses") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            Handler.succeed(Response(status = Status.Ok, body = Body.fromString("auto"))),
          ),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              response <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/", body = Chunk.empty, streamId = 1))
            } yield assertTrue(
              response.status == 200,
              response.bodyText == "auto",
              response.headerValue("content-length").contains("4"),
            )
          }
        }
      },
      test("path with multiple segments dispatches correctly") {
        val routes = Routes(
          Route(
            RoutePattern(Method.GET, "/api/v1/users"),
            Handler.succeed(Response.text("users")),
          ),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              response <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/api/v1/users", body = Chunk.empty, streamId = 1))
            } yield assertTrue(response.status == 200, response.bodyText == "users")
          }
        }
      },
      test("PUT DELETE and PATCH methods dispatch correctly") {
        val routes = Routes(
          Route(RoutePattern.PUT, Handler.succeed(Response.text("put"))),
          Route(RoutePattern.DELETE, Handler.succeed(Response.text("delete"))),
          Route(RoutePattern.PATCH, Handler.succeed(Response.text("patch"))),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              put    <- ZIO.attemptBlocking(client.roundTrip(method = "PUT", path = "/", body = Chunk.empty, streamId = 1))
              delete <- ZIO.attemptBlocking(client.roundTrip(method = "DELETE", path = "/", body = Chunk.empty, streamId = 3))
              patch  <- ZIO.attemptBlocking(client.roundTrip(method = "PATCH", path = "/", body = Chunk.empty, streamId = 5))
            } yield assertTrue(
              put.status == 200,
              put.bodyText == "put",
              delete.status == 200,
              delete.bodyText == "delete",
              patch.status == 200,
              patch.bodyText == "patch",
            )
          }
        }
      },
      test("multiple routes dispatch correctly and unknown path returns 404") {
        val routes = Routes(
          Route(
            RoutePattern(Method.GET, "/hello"),
            Handler.succeed(Response(status = Status.Ok, body = Body.fromString("hello"))),
          ),
          Route(
            RoutePattern(Method.GET, "/world"),
            Handler.succeed(Response(status = Status.Ok, body = Body.fromString("world"))),
          ),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              hello   <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/hello", body = Chunk.empty, streamId = 1))
              world   <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/world", body = Chunk.empty, streamId = 3))
              unknown <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/unknown", body = Chunk.empty, streamId = 5))
            } yield assertTrue(
              hello.status == 200,
              hello.bodyText == "hello",
              world.status == 200,
              world.bodyText == "world",
              unknown.status == 404,
            )
          }
        }
      },
      test("concurrent connections stay isolated across separate sockets") {
        val routes = Routes(
          Route(
            RoutePattern.POST,
            handler { (req: Request) =>
              responseAsResult(Response(status = Status.Ok, body = req.body))
            },
          ),
        )

        withServer(routes) { port =>
          withClient(port) { firstClient =>
            withClient(port) { secondClient =>
              val firstBody  = Chunk.fromArray("first-connection".getBytes(StandardCharsets.UTF_8))
              val secondBody = Chunk.fromArray("second-connection".getBytes(StandardCharsets.UTF_8))

              for {
                responses <- ZIO.collectAllPar(
                               List(
                                 ZIO.attemptBlocking(firstClient.roundTrip(method = "POST", path = "/", body = firstBody, streamId = 1)),
                                 ZIO.attemptBlocking(secondClient.roundTrip(method = "POST", path = "/", body = secondBody, streamId = 1)),
                               ),
                             )
                first      = responses.head
                second     = responses(1)
              } yield assertTrue(
                first.status == 200,
                first.body == firstBody,
                second.status == 200,
                second.body == secondBody,
              )
            }
          }
        }
      },
      test("defect handler default produces 500") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              (throw new RuntimeException("boom")): Response | Halt
            },
          ),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              response <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/", body = Chunk.empty, streamId = 1))
            } yield assertTrue(response.status == 500)
          }
        }
      },
      test("custom defect handler can produce 503") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              (throw new RuntimeException("boom")): Response | Halt
            },
          ),
        )
        val defectHandler = new DefectHandler {
          override def handleDefect(request: Request, throwable: Throwable) =
            responseAsResult(Response(Status.ServiceUnavailable))
        }

        withServer(routes, defectHandler = defectHandler) { port =>
          withClient(port) { client =>
            for {
              response <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/", body = Chunk.empty, streamId = 1))
            } yield assertTrue(response.status == 503)
          }
        }
      },
      test("server shutdown cleanly closes listener") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

        ZIO.acquireRelease(startServer(routes)) { handle =>
          ZIO.succeed(handle.shutdownAndWait())
        }.flatMap { handle =>
          val port = tcpPort(handle)
          for {
            _              <- ZIO.attempt(handle.isRunning).map(running => assertTrue(running))
            _              <- ZIO.attempt(handle.shutdownAndWait())
            stoppedRunning <- ZIO.attempt(handle.isRunning)
            connectResult  <- ZIO.attemptBlocking(attemptSocketConnect(port)).either
          } yield assertTrue(!stoppedRunning, connectResult.isLeft)
        }
      },
      test("multiple concurrent requests on one H2C connection all succeed") {
        val routes = Routes(
          Route(RoutePattern(Method.GET, "/one"), Handler.succeed(Response(status = Status.Ok, body = Body.fromString("one")))),
          Route(RoutePattern(Method.GET, "/two"), Handler.succeed(Response(status = Status.Ok, body = Body.fromString("two")))),
          Route(RoutePattern(Method.GET, "/three"), Handler.succeed(Response(status = Status.Ok, body = Body.fromString("three")))),
        )

        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              responses <- ZIO.attemptBlocking {
                             client.sendRequest(method = "GET", path = "/one", body = Chunk.empty, streamId = 1)
                             client.sendRequest(method = "GET", path = "/two", body = Chunk.empty, streamId = 3)
                             client.sendRequest(method = "GET", path = "/three", body = Chunk.empty, streamId = 5)
                             client.awaitResponses(Set(1, 3, 5))
                           }
            } yield assertTrue(
              responses(1).status == 200,
              responses(1).bodyText == "one",
              responses(3).status == 200,
              responses(3).bodyText == "two",
              responses(5).status == 200,
              responses(5).bodyText == "three",
            )
          }
        }
      },
      test("middleware-transformed routes are served") {
        val baseRoutes = Routes(
          Route(RoutePattern(Method.GET, "/base"), Handler.succeed(Response(status = Status.Ok, body = Body.fromString("base")))),
        )
        val middleware = new Middleware[Any, Any] {
          override def apply(routes: Routes[Any]): Routes[Any] =
            routes ++ Routes(
              Route(
                RoutePattern(Method.GET, "/middleware"),
                Handler.succeed(Response(status = Status.Ok, body = Body.fromString("middleware"))),
              ),
            )
        }

        withServer(baseRoutes @@ middleware) { port =>
          withClient(port) { client =>
            for {
              base       <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/base", body = Chunk.empty, streamId = 1))
              middleware <- ZIO.attemptBlocking(client.roundTrip(method = "GET", path = "/middleware", body = Chunk.empty, streamId = 3))
            } yield assertTrue(
              base.status == 200,
              base.bodyText == "base",
              middleware.status == 200,
              middleware.bodyText == "middleware",
            )
          }
        }
      },
    ) @@ sequential

  private def withServer[R](
    routes: Routes[Any],
    defectHandler: DefectHandler = DefectHandler.default,
  )(use: Int => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] =
    ZIO.acquireRelease(startServer(routes, defectHandler)) { handle =>
      ZIO.succeed(handle.shutdownAndWait())
    }.flatMap(handle => use(tcpPort(handle)))

  private def withClient[R](port: Int)(use: H2cTestClient => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking(new H2cTestClient(port)),
    )(client => ZIO.succeed(client.close())).flatMap(use)

  private def startServer(routes: Routes[Any], defectHandler: DefectHandler = DefectHandler.default): Task[ServerHandle] =
    ZIO.attempt(ServerHandle.live(List(new H2Transport(routes, Context.empty, Connector(bind = BindAddress.localhost(0)), defectHandler).start())))

  private def tcpPort(handle: ServerHandle): Int =
    handle.bindings.head.address match {
      case BoundAddress.Tcp(_, port) => port
      case other                     => throw new AssertionError("Expected TCP binding but found: " + other)
    }

  private def attemptSocketConnect(port: Int): Unit = {
    val socket = new Socket()
    try socket.connect(new InetSocketAddress("127.0.0.1", port), 300)
    finally socket.close()
  }

  private final case class ReceivedResponse(
    status: Int,
    headers: List[HeaderField],
    body: Chunk[Byte],
  ) {
    def bodyText: String = new String(body.toArray, StandardCharsets.UTF_8)

    def headerValue(name: String): Option[String] =
      headers.find(_.name.equalsIgnoreCase(name)).map(_.value)
  }

  private object H2cTestClient {
    private val ClientPreface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
  }

  private final class H2cTestClient(port: Int) extends AutoCloseable {
    import H2cTestClient._

    private val socket = new Socket("127.0.0.1", port)
    socket.setSoTimeout(5000)

    private val input  = new FrameReader(socket.getInputStream)
    private val output = socket.getOutputStream

    handshake()

    def roundTrip(method: String, path: String, body: Chunk[Byte], streamId: Int): ReceivedResponse = {
      sendRequest(method, path, body, streamId)
      awaitResponse(streamId)
    }

    def sendRequest(method: String, path: String, body: Chunk[Byte], streamId: Int): Unit = {
      output.write(FrameCodec.encode(requestHeaders(method, path, body, streamId)).toArray)
      if (body.nonEmpty) output.write(FrameCodec.encode(Data(streamId, body, endStream = true)).toArray)
      output.flush()
    }

    def awaitResponse(streamId: Int): ReceivedResponse =
      awaitResponses(Set(streamId))(streamId)

    def awaitResponses(streamIds: Set[Int]): Map[Int, ReceivedResponse] = {
      val builders = mutable.Map.empty[Int, ResponseBuilder]
      val pending  = mutable.Set.empty[Int] ++ streamIds

      while (pending.nonEmpty) {
        input.readFrame() match {
          case Settings(true, _)                 => ()
          case Settings(false, _)                =>
            output.write(FrameCodec.encode(Settings(ack = true, Nil)).toArray)
            output.flush()
          case Headers(streamId, headerBlock, endStream, _, _, _) if streamIds.contains(streamId) =>
            val decoded = decodeHeaders(headerBlock)
            val builder = builders.getOrElseUpdate(streamId, ResponseBuilder.empty(decoded))
            builders.update(streamId, builder.withHeaders(decoded))
            if (endStream) pending -= streamId
          case Data(streamId, data, endStream, _) if streamIds.contains(streamId)                  =>
            val builder = builders.getOrElseUpdate(streamId, ResponseBuilder.empty(Nil))
            builders.update(streamId, builder.appendBody(data))
            if (endStream) pending -= streamId
          case WindowUpdate(_, _)                => ()
          case GoAway(_, errorCode, debugData)   =>
            val debug = new String(debugData.toArray, StandardCharsets.UTF_8)
            throw new AssertionError(s"Server sent GOAWAY: error=$errorCode debug=$debug")
          case other                             =>
            throw new AssertionError("Unexpected frame while waiting for response: " + other)
        }
      }

      streamIds.iterator.map { streamId =>
        val builder = builders.getOrElse(streamId, throw new AssertionError(s"Missing response for stream $streamId"))
        streamId -> builder.result
      }.toMap
    }

    override def close(): Unit = socket.close()

    private def handshake(): Unit = {
      output.write(ClientPreface)
      output.write(FrameCodec.encode(Settings(ack = false, Nil)).toArray)
      output.flush()

      input.readFrame() match {
        case Settings(false, _) =>
          output.write(FrameCodec.encode(Settings(ack = true, Nil)).toArray)
          output.flush()
        case other              =>
          throw new AssertionError("Expected server SETTINGS frame but received: " + other)
      }
    }

    private def requestHeaders(method: String, path: String, body: Chunk[Byte], streamId: Int): Headers = {
      val requestHeaders = List(
        HeaderField(":method", method),
        HeaderField(":path", path),
        HeaderField(":scheme", "http"),
        HeaderField(":authority", s"127.0.0.1:$port"),
      ) ++ {
        if (body.isEmpty) Nil
        else List(HeaderField("content-length", body.length.toString))
      }

      Headers(
        streamId = streamId,
        headerBlock = Hpack.encode(requestHeaders),
        endStream = body.isEmpty,
        endHeaders = true,
      )
    }

    private def decodeHeaders(headerBlock: Chunk[Byte]): List[HeaderField] =
      Hpack.decode(headerBlock) match {
        case Right(headers) => headers
        case Left(error)    => throw new AssertionError("Failed to decode response headers: " + error)
      }
  }

  private final case class ResponseBuilder(headers: List[HeaderField], body: Chunk[Byte]) {
    def appendBody(chunk: Chunk[Byte]): ResponseBuilder = copy(body = body ++ chunk)

    def withHeaders(value: List[HeaderField]): ResponseBuilder = copy(headers = value)

    def result: ReceivedResponse = {
      val status = headers.find(_.name == ":status").map(_.value.toInt).getOrElse {
        throw new AssertionError("Missing :status response header")
      }
      ReceivedResponse(status = status, headers = headers, body = body)
    }
  }

  private object ResponseBuilder {
    def empty(headers: List[HeaderField]): ResponseBuilder = ResponseBuilder(headers, Chunk.empty)
  }

  private final class FrameReader(input: InputStream) {
    private var buffer = Chunk.empty[Byte]

    def readFrame(): H2Frame = {
      while (true) {
        FrameCodec.decode(buffer) match {
          case Right((decoded, rest))         =>
            buffer = rest
            return decoded
          case Left(H2Error.InsufficientData) =>
            val chunk = new Array[Byte](8192)
            val read  = input.read(chunk)
            if (read < 0) throw new AssertionError("Connection closed before an HTTP/2 frame was fully received")
            buffer = buffer ++ Chunk.fromArray(java.util.Arrays.copyOf(chunk, read))
          case Left(error)                    =>
            throw new AssertionError("Failed to decode HTTP/2 frame: " + error)
        }
      }
      throw new AssertionError("Unreachable HTTP/2 frame read state")
    }
  }
}
