package zio.http

import zio.ZLayer
import zio.http.service.ChannelFactory

package object internal {
  type HttpEnv = ChannelFactory with DynamicServer

  val testServerConfig: ZLayer[Any, Nothing, ServerConfig] =
    ZLayer.succeed(ServerConfig.default.port(0).leakDetection(LeakDetectionLevel.PARANOID))

  val severTestLayer = testServerConfig >>> Server.live
}
