package example

import io.netty.util.AsciiString
import zio._
import zio.http._

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

  private val config = ServerConfig.default
    .withPort(8000)
    .withMaxThreads(8)
    .withLeakDetection(LeakDetectionLevel.DISABLED)
    .withConsolidateFlush(true)
    .withFlowControl(false)
    .withObjectAggregator(-1)

  private val configLayer = ServerConfigLayer.live(config)

  val run: UIO[ExitCode] =
    app
      .flatMap(Server.serve(_).provide(configLayer, Server.live))
      .exitCode

}
