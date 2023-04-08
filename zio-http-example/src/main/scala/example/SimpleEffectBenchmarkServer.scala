package example

import zio._

import zio.http.Server.RequestStreaming
import zio.http._
import zio.http.model.{Header, Method}
import zio.http.netty.NettyConfig
import zio.http.netty.NettyConfig.LeakDetectionLevel

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

  private val config = Server.Config.default
    .port(8080)
    .requestStreaming(RequestStreaming.Enabled)

  private val nettyConfig = NettyConfig.default
    .leakDetection(LeakDetectionLevel.DISABLED)
    .maxThreads(8)

  private val configLayer      = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  val run: UIO[ExitCode] =
    Server.serve(app).provide(configLayer, nettyConfigLayer, Server.customized).exitCode

}
