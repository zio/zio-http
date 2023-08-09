package example

import zio._

import zio.http._
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
//    .serverTime
    .addHeader(Header.Server(STATIC_SERVER_NAME))

  private val frozenPlainTextResponse = Response
    .text(plainTextMessage)
//    .serverTime
    .addHeader(Header.Server(STATIC_SERVER_NAME))

  private def plainTextApp(response: Response): HttpApp[Any] =
    Routes(Method.GET / plaintextPath -> Handler.response(response)).toHttpApp

  private def jsonApp(json: Response): HttpApp[Any] =
    Routes(Method.GET / jsonPath -> Handler.response(json)).toHttpApp

  val app: HttpApp[Any] = plainTextApp(frozenPlainTextResponse) ++ jsonApp(frozenJsonResponse)

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
