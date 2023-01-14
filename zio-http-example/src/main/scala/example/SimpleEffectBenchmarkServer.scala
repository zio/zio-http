package example

import io.netty.util.AsciiString
import zio._
import zio.http.ServerConfig.LeakDetectionLevel
import zio.http._
import zio.http.model.Method

/**
 * This server is used to run plaintext benchmarks on CI.
 */
object SimpleEffectBenchmarkServer extends ZIOAppDefault {

  private val plainTextMessage: String = "hello, world!"
  private val jsonMessage: String      = s"""{"message": "$plainTextMessage"}"""

  private val STATIC_SERVER_NAME = AsciiString.cached("zio-http")

  private val app: HttpRoute[Any, Nothing] = Http.collectZIO[Request] {
    case Method.GET -> !! / "plaintext" =>
      ZIO.succeed(Response.text(plainTextMessage).withServerTime.withServer(STATIC_SERVER_NAME).freeze)
    case Method.GET -> !! / "json"      =>
      ZIO.succeed(Response.json(jsonMessage).withServerTime.withServer(STATIC_SERVER_NAME).freeze)
  }

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
