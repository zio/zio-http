package example

import io.netty.util.AsciiString
import zio._
import zio.http.ServerConfig.LeakDetectionLevel
import zio.http._

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object PlainTextBenchmarkServer extends ZIOAppDefault {

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

  private def plainTextApp(response: Response): HttpRoute[Any, Nothing] =
    Handler.response(response).toRoute.whenPathEq(plaintextPath)

  private def jsonApp(json: Response): HttpRoute[Any, Nothing] =
    Handler.response(json).toRoute.whenPathEq(jsonPath)

  val app = plainTextApp(frozenPlainTextResponse) ++ jsonApp(frozenJsonResponse)

  private val config = ServerConfig.default
    .port(8080)
    .maxThreads(8)
    .leakDetection(LeakDetectionLevel.DISABLED)
    .consolidateFlush(true)
    .flowControl(false)
    .objectAggregator(-1)

  private val configLayer = ServerConfig.live(config)

  val run: UIO[ExitCode] =
    Server.serve(app).provide(configLayer, Server.live).exitCode

}
