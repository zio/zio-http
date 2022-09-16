package example

import io.netty.util.AsciiString
import zio._
import zio.http.Server.LeakDetectionLevel
import zio.http._
import zio.http.model.Response
import zio.http.service.{EventLoopGroup, ServerChannelFactory}

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object Main extends ZIOAppDefault {

  private val plainTextMessage: String = "Hello, World!"
  private val jsonMessage: String      = """{"greetings": "Hello World!"}"""

  private val plaintextPath = "/plaintext"
  private val jsonPath      = "/json"

  private val STATIC_SERVER_NAME = AsciiString.cached("zio-http")

  private val frozenJsonResponse = Response
    .json(jsonMessage)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)
    .freeze

  private val frozenPlainTextResponse = Response
    .text(plainTextMessage)
    .withServerTime
    .withServer(STATIC_SERVER_NAME)
    .freeze

  private def plainTextApp(response: Response) = Http.fromHExit(HExit.succeed(response)).whenPathEq(plaintextPath)

  private def jsonApp(json: Response) = Http.fromHExit(HExit.succeed(json)).whenPathEq(jsonPath)

  private def app = for {
    plainTextResponse <- frozenPlainTextResponse
    jsonResponse      <- frozenJsonResponse
  } yield plainTextApp(plainTextResponse) ++ jsonApp(jsonResponse)

  val run: UIO[ExitCode] =
    app
      .flatMap(server(_).start)
      .provideLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(8))
      .exitCode

  private def server(app: HttpApp[Any, Nothing]) =
    Server
      .app(app)
      .withPort(8080)
      .withError(_ => ZIO.unit)
      .withLeakDetection(LeakDetectionLevel.DISABLED)
      .withConsolidateFlush(true)
      .withFlowControl(false)
      .withObjectAggregator(-1)
}
