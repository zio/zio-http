package example

import zio._

import zio.http._
import zio.http.model.{Header, Method}
import zio.http.netty.NettyServerConfig
import zio.http.netty.NettyServerConfig.LeakDetectionLevel

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object SimpleEffectBenchmarkServer extends ZIOAppDefault {

  private val plainTextMessage: String = "hello, world!"
  private val jsonMessage: String      = s"""{"message": "$plainTextMessage"}"""

  private val STATIC_SERVER_NAME = "zio-http"

  private val app: HttpApp[Any, Nothing] = Http.collectZIO[Request] {
    case Method.GET -> !! / "plaintext" =>
      ZIO.succeed(
        Response
          .text(plainTextMessage)
          .withServerTime
          .withHeader(Header.Server(STATIC_SERVER_NAME))
          .freeze,
      )
    case Method.GET -> !! / "json"      =>
      ZIO.succeed(
        Response
          .json(jsonMessage)
          .withServerTime
          .withHeader(Header.Server(STATIC_SERVER_NAME))
          .freeze,
      )
  }

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
