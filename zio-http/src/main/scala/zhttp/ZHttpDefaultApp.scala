package zhttp

import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ChannelFactory, EventLoopGroup, ServerChannelFactory}
import zio.{EnvironmentTag, ZIOApp}

/**
 * Default http environment
 */
trait ZHttpDefaultApp extends ZIOApp {
  type Environment = EventLoopGroup with ChannelFactory with ServerChannelFactory

  override val bootstrap = EventLoopGroup.auto(0) ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

}
