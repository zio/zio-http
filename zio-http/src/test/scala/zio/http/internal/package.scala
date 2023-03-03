package zio.http

import zio.ZLayer

import zio.http.netty.NettyServerConfig
import zio.http.netty.NettyServerConfig.LeakDetectionLevel

package object internal {

  val testServerConfig: ZLayer[Any, Nothing, ServerConfig] =
    ZLayer.succeed(ServerConfig.default.port(0))

  val testNettyServerConfig: ZLayer[Any, Nothing, NettyServerConfig] =
    ZLayer.succeed(NettyServerConfig.default.leakDetection(LeakDetectionLevel.PARANOID))

  val severTestLayer: ZLayer[Any, Throwable, ServerConfig with Server] =
    ZLayer.make[ServerConfig with Server](
      testServerConfig,
      testNettyServerConfig,
      Server.live,
    )
}
