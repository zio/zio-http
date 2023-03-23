package example

import zio._

import zio.http._
import zio.http.model.Header
import zio.http.netty.NettyServerConfig
import zio.http.netty.NettyServerConfig.LeakDetectionLevel

import io.netty.util.AsciiString

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object PlainTextBenchmarkServer extends ZIOAppDefault {

  private val plainTextMessage: String = "hello, world!"
  private val jsonMessage: String      = """{"message": "hello, world!"}"""

  private val plaintextPath = "/plaintext"
  private val jsonPath      = "/json"

  private val STATIC_SERVER_NAME = "zio-http"

  private val frozenJsonResponse = Response
    .json(jsonMessage)
    .withServerTime
    .withHeader(Header.Server(STATIC_SERVER_NAME))
    .freeze

  private val frozenPlainTextResponse = Response
    .text(plainTextMessage)
    .withServerTime
    .withHeader(Header.Server(STATIC_SERVER_NAME))
    .freeze

  private def plainTextApp(response: Response): HttpApp[Any, Nothing] =
    Handler.response(response).toHttp.whenPathEq(plaintextPath)

  private def jsonApp(json: Response): HttpApp[Any, Nothing] =
    Handler.response(json).toHttp.whenPathEq(jsonPath)

  val app = plainTextApp(frozenPlainTextResponse) ++ jsonApp(frozenJsonResponse)

  private val config = ServerConfig.default
    .port(8080)
    .maxThreads(8)
    .consolidateFlush(true)
    .flowControl(false)
    .objectAggregator(-1)

  private val nettyConfig = NettyServerConfig.default
    .leakDetection(LeakDetectionLevel.DISABLED)

  private val configLayer      = ServerConfig.live(config)
  private val nettyConfigLayer = NettyServerConfig.live(nettyConfig)

  val run: UIO[ExitCode] =
    Server.serve(app).provide(configLayer, nettyConfigLayer, Server.customized).exitCode

}
