package zhttp

import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.{EnvironmentTag, ZIOApp}

trait ZHttpDefaultClientApp extends ZIOApp {
  type Environment = EventLoopGroup with ChannelFactory

  override val bootstrap = EventLoopGroup.auto(0) ++ ChannelFactory.auto

  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

}
