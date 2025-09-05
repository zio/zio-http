//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.NettyConfig.LeakDetectionLevel

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object SimpleEffectBenchmarkServer extends ZIOAppDefault {

  private val plainTextMessage: String = "hello, world!"
  private val jsonMessage: String      = s"""{"message": "$plainTextMessage"}"""

  private val STATIC_SERVER_NAME = "zio-http"

  private val routes: Routes[Any, Response] = Routes(
    Method.GET / "plaintext" ->
      handler(
        Response
          .text(plainTextMessage)
          .addHeader(Header.Server(STATIC_SERVER_NAME)),
      ),
    Method.GET / "json"      ->
      handler(
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

  override val run =
    Server.serve(routes).provide(configLayer, nettyConfigLayer, Server.customized)

}
