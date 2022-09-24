package example

import io.netty.util.AsciiString
import zio._
import zio.http.ServerConfig.LeakDetectionLevel
import zio.http._

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object Main extends ZIOAppDefault {

  private val plainTextMessage: String = "hello, world!"
  private val jsonMessage: String      = """{"message": "hello, world!"}"""

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

  private def plainTextApp(response: Response) =
    Http.fromHExit(HExit.succeed(response)).whenPathEq(plaintextPath)

  private def jsonApp(json: Response) =
    Http.fromHExit(HExit.succeed(json)).whenPathEq(jsonPath)

  private def app = for {
    plainTextResponse <- frozenPlainTextResponse
    jsonResponse      <- frozenJsonResponse
  } yield plainTextApp(plainTextResponse) ++ jsonApp(jsonResponse)

  private val config = ServerConfig.default
    .port(8080)
    .maxThreads(8)
    .leakDetection(LeakDetectionLevel.DISABLED)
    .consolidateFlush(true)
    .flowControl(false)
    .objectAggregator(-1)

  private val configLayer = ServerConfig.live(config)

  val run: UIO[ExitCode] =
    app
      .flatMap(Server.serve(_).provide(configLayer, Server.live))
      .exitCode

}
