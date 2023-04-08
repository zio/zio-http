package example

import zio._

import zio.http.Server.RequestStreaming
import zio.http._
import zio.http.model.Header
import zio.http.netty.NettyConfig
import zio.http.netty.NettyConfig.LeakDetectionLevel

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

  val app: App[Any] = plainTextApp(frozenPlainTextResponse) ++ jsonApp(frozenJsonResponse)

  private val config = Server.Config.default
    .port(8080)
    .enableRequestStreaming

  private val nettyConfig = NettyConfig.default
    .leakDetection(LeakDetectionLevel.DISABLED)
    .maxThreads(8)

  private val configLayer      = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  val run: UIO[ExitCode] =
    Server.serve(app).provide(configLayer, nettyConfigLayer, Server.customized).exitCode

}
