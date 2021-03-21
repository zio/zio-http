package zhttp.service

import io.netty.util.{ResourceLeakDetector => JResourceLeakDetector}
import zhttp.core._
import zhttp.http.{Http, Request, Response, Status, _}
import zhttp.service.server.{LeakDetectionLevel, ServerChannelFactory, ServerChannelInitializer, ServerRequestHandler}
import zio.{ZManaged, _}

sealed trait Server[-R, +E] { self =>

  import Server._

  def ++[R1 <: R, E1 >: E](other: Server[R1, E1]): Server[R1, E1] =
    Server.Concat(self, other)

  private def settings[R1 <: R, E1 >: E](s: Settings[R1, E1] = Settings()): Server.Settings[R1, E1] = self match {
    case Server.Concat(self, other)  => other.settings(self.settings(s))
    case Server.Port(port)           => s.copy(port = port)
    case Server.LeakDetection(level) => s.copy(leakDetectionLevel = level)
    case Server.App(http)            => s.copy(http = http)
  }

  def make[E1 >: E: SilentResponse]: ZManaged[R with EventLoopGroup, Throwable, Unit] = Server.make(self)
}

object Server {
  private case class Settings[-R, +E](
    http: Http[R, E, Request, Response] = Http.empty(Status.NOT_FOUND),
    port: Int = 8080,
    leakDetectionLevel: LeakDetectionLevel = LeakDetectionLevel.SIMPLE,
  )

  private case class Concat[R, E](self: Server[R, E], other: Server[R, E]) extends Server[R, E]
  private case class Port(port: Int)                                       extends UServerConfiguration
  private case class LeakDetection(level: LeakDetectionLevel)              extends UServerConfiguration
  private case class App[R, E](http: Http[R, E, Request, Response])        extends Server[R, E]

  def app[R, E](http: Http[R, E, Request, Response]): Server[R, E] = Server.App(http)

  def port(int: Int): UServerConfiguration        = Server.Port(int)
  val disableLeakDetection: UServerConfiguration  = LeakDetection(LeakDetectionLevel.DISABLED)
  val simpleLeakDetection: UServerConfiguration   = LeakDetection(LeakDetectionLevel.SIMPLE)
  val advancedLeakDetection: UServerConfiguration = LeakDetection(LeakDetectionLevel.ADVANCED)
  val paranoidLeakDetection: UServerConfiguration = LeakDetection(LeakDetectionLevel.PARANOID)

  /**
   * Launches the app on the provided port.
   */
  def start[R <: Has[_], E: SilentResponse](port: Int, http: HttpApp[R, E]): ZIO[R, Throwable, Nothing] =
    (Server.port(port) ++ Server.app(http)).make.useForever
      .provideSomeLayer[R](EventLoopGroup.auto())

  def make[R, E: SilentResponse](server: Server[R, E]): ZManaged[R with EventLoopGroup, Throwable, Unit] = {
    for {
      zExec          <- UnsafeChannelExecutor.make[R].toManaged_
      channelFactory <- ServerChannelFactory.Live.auto.toManaged_
      eventLoopGroup <- ZIO.access[EventLoopGroup](_.get).toManaged_
      settings        = server.settings()
      httpH           = ServerRequestHandler(zExec, settings.http.silent)
      init            = ServerChannelInitializer(httpH)
      serverBootstrap = new JServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
      _ <- ChannelFuture.asManaged(serverBootstrap.childHandler(init).bind(settings.port))
    } yield {
      JResourceLeakDetector.setLevel(settings.leakDetectionLevel.jResourceLeakDetectionLevel)
    }
  }
}
