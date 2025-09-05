//> using dep "dev.zio::zio-http:3.4.1"

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

  private val configLayer      = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  override val run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    Server.serve(routes).provide(configLayer, nettyConfigLayer, Server.customized)

}
