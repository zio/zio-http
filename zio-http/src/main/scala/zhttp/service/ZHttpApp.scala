package zhttp.service

import zhttp.service.server.ServerChannelFactory
import zio._

/**
 * Default http environment
 */
trait ZHttpApp extends ZIOApp {
  type Environment = Any

  override val bootstrap = EventLoopGroup.auto(0) ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  val environmentTag: EnvironmentTag[Any] = EnvironmentTag[Any]

  def runHttpApp: ZIO[ChannelFactory with EventLoopGroup with ServerChannelFactory, Any, Any]

  override def run = runHttpApp.provide(bootstrap)

}
