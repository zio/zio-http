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

  val routes: Routes[Any, Response] = Routes(
    Method.GET / plaintextPath ->
      Handler.fromResponse(
        Response
          .text(plainTextMessage)
          .addHeader(Header.Server(STATIC_SERVER_NAME)),
      ),
    Method.GET / jsonPath      ->
      Handler.fromResponse(
        Response
          .json(jsonMessage)
          .addHeader(Header.Server(STATIC_SERVER_NAME)),
      ),
  )

  private val config = Server.Config.default
    .port(8080)

  private val nettyConfig = NettyConfig.default
    .leakDetection(LeakDetectionLevel.DISABLED)

  private val configLayer              = ZLayer.succeed(config)
  private val nettyConfigLayer         = ZLayer.succeed(nettyConfig)
  private val serverRuntimeConfigLayer = configLayer.flatMap(env => ZLayer.succeed(ServerRuntimeConfig(env.get)))

  val run: UIO[ExitCode] =
    Server.serve(routes).provide(serverRuntimeConfigLayer, nettyConfigLayer, Server.customized).exitCode

}
