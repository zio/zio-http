package example

import zio._

import zio.http._
import zio.http.netty.NettyConfig.LeakDetectionLevel
import zio.http.netty.{ChannelType, NettyConfig}

import io.netty.channel.epoll.Epoll
import io.netty.channel.kqueue.KQueue

/**
 * This server is used to run the effectful plaintext benchmarks on CI.
 */
object SimpleEffectBenchmarkServer extends ZIOAppDefault {

  private val plainTextMessage: String = "hello, world!"
  private val jsonMessage: String      = s"""{"message": "$plainTextMessage"}"""

  private val STATIC_SERVER_NAME = "zio-http"

  private val app: Routes[Any, Response] = Routes(
    Method.GET / "plaintext" ->
      handler(
        ZIO.succeed {
          Response
            .text(plainTextMessage)
            .addHeader(Header.Server(STATIC_SERVER_NAME))
        },
      ),
    Method.GET / "json"      ->
      handler(
        ZIO.succeed {
          Response
            .json(jsonMessage)
            .addHeader(Header.Server(STATIC_SERVER_NAME))
        },
      ),
  )

  private val config = Server.Config.default
    .port(8080)
    .enableRequestStreaming

  private val nettyConfig = ZLayer {
    ZIO.serviceWith[ChannelType] { ct =>
      NettyConfig.default
        .leakDetection(LeakDetectionLevel.DISABLED)
        .maxThreads(8)
        .channelType(ct)
    }
  }

  private val channelTypeLayer: TaskLayer[ChannelType] = ZLayer(ZIO.suspendSucceed {
    if (Epoll.isAvailable) ZIO.succeed(ChannelType.EPOLL)        // Linux
    else if (KQueue.isAvailable) ZIO.succeed(ChannelType.KQUEUE) // MacOS
    else ZIO.fail(new Throwable("KQueue or Epoll required for benchmark server"))
  })

  private val configLayer      = ZLayer.succeed(config)
  private val nettyConfigLayer = channelTypeLayer >>> nettyConfig

  val run: UIO[ExitCode] =
    Server.serve(app).provide(configLayer, nettyConfigLayer, Server.customized).exitCode

}
