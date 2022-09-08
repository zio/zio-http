package zhttp

import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, ServerChannelFactory}
import zio.{EnvironmentTag, ZIOApp}

/**
 * Default http environment
 */
trait ZHttpDefaultServerApp extends ZIOApp {
  type Environment = EventLoopGroup with ServerChannelFactory

  override val bootstrap = EventLoopGroup.auto(0) ++ ServerChannelFactory.auto

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

}
