package zio.http

import zio.ZLayer
import zio.http.ServerConfig.LeakDetectionLevel

package object internal {

  val testServerConfig: ZLayer[Any, Nothing, ServerConfig] =
    ZLayer.succeed(ServerConfig.default.port(0).leakDetection(LeakDetectionLevel.PARANOID))

  val severTestLayer: ZLayer[Any, Throwable, ServerConfig with Server] = testServerConfig >+> Server.live
}
