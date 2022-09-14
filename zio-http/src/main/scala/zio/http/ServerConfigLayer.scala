package zio.http

import zio.ZLayer

object ServerConfigLayer {

  val default: ZLayer[Any, Nothing, ServerConfig] =
    ZLayer.succeed(ServerConfig.default)

  def live(config: ServerConfig): ZLayer[Any, Nothing, ServerConfig] = ZLayer.succeed(config)


  val testServerConfig: ZLayer[Any, Nothing, ServerConfig] =
    ZLayer.succeed(ServerConfig.default.port(0).leakDetection(LeakDetectionLevel.PARANOID))

}
