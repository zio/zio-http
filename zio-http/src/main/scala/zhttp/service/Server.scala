package zhttp.service

import io.netty.util.{ResourceLeakDetector => JResourceLeakDetector}
import zhttp.core._
import zhttp.http.{Status, _}
import zhttp.service.server.{
  LeakDetectionLevel,
  ServerChannelFactory,
  ServerChannelInitializer,
  ServerRequestHandler,
  StreamingRequestHandler,
}
import zio.{ZManaged, _}

sealed trait Server[-R, +E] { self =>

  import Server._

  def ++[R1 <: R, E1 >: E](other: Server[R1, E1]): Server[R1, E1] =
    Concat(self, other)

  private def settings[R1 <: R, E1 >: E](s: Settings[R1, E1] = Settings()): Settings[R1, E1] = self match {
    case Concat(self, other)  => other.settings(self.settings(s))
    case Port(port)           => s.copy(port = port)
    case LeakDetection(level) => s.copy(leakDetectionLevel = level)
    case App(http)            => s.copy(http = http)
    case MaxRequestSize(size) => s.copy(maxRequestSize = size)
    case StreamingRequests    => s.copy(streamingRequests = true)
  }

  def make(implicit ev: E <:< Throwable): ZManaged[R with EventLoopGroup with ServerChannelFactory, Throwable, Unit] =
    Server.make(ev.liftCo(self))

  def start(implicit ev: E <:< Throwable): ZIO[R with EventLoopGroup with ServerChannelFactory, Throwable, Nothing] =
    make.useForever
}

object Server {
  private case class Settings[-R, +E](
    http: Http[R, E] = Http.empty(Status.NOT_FOUND),
    port: Int = 8080,
    leakDetectionLevel: LeakDetectionLevel = LeakDetectionLevel.SIMPLE,
    maxRequestSize: Int = 4 * 1024, // 4 kilo bytes. Only used in non-streaming setup
    streamingRequests: Boolean = false,
  )

  private case class Concat[R, E](self: Server[R, E], other: Server[R, E]) extends Server[R, E]
  private case class Port(port: Int)                                       extends UServer
  private case class LeakDetection(level: LeakDetectionLevel)              extends UServer
  private case class MaxRequestSize(size: Int)                             extends UServer
  private case object StreamingRequests                                    extends UServer
  private case class App[R, E](http: Http[R, E])                           extends Server[R, E]

  def app[R, E](http: Http[R, E]): Server[R, E] = Server.App(http)
  def maxRequestSize(size: Int): UServer        = Server.MaxRequestSize(size)
  def port(int: Int): UServer                   = Server.Port(int)
  def streamingRequests: UServer                = Server.StreamingRequests
  val disableLeakDetection: UServer             = LeakDetection(LeakDetectionLevel.DISABLED)
  val simpleLeakDetection: UServer              = LeakDetection(LeakDetectionLevel.SIMPLE)
  val advancedLeakDetection: UServer            = LeakDetection(LeakDetectionLevel.ADVANCED)
  val paranoidLeakDetection: UServer            = LeakDetection(LeakDetectionLevel.PARANOID)

  /**
   * Launches the app on the provided port.
   */
  def start[R <: Has[_]](port: Int, http: RHttp[R]): ZIO[R, Throwable, Nothing] =
    (Server.port(port) ++ Server.app(http)).make.useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def make[R](
    server: Server[R, Throwable],
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory, Throwable, Unit] = {
    val settings = server.settings()
    for {
      zExec          <- UnsafeChannelExecutor.make[R].toManaged_
      channelFactory <- ZManaged.access[ServerChannelFactory](_.get)
      eventLoopGroup <- ZManaged.access[EventLoopGroup](_.get)
      httpH           =
        if (settings.streamingRequests) StreamingRequestHandler(zExec, settings.http)
        else ServerRequestHandler(zExec, settings.http)
      init            = ServerChannelInitializer(httpH, settings.maxRequestSize)
      serverBootstrap = new JServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
      _ <- ChannelFuture.asManaged(serverBootstrap.childHandler(init).bind(settings.port))
    } yield {
      JResourceLeakDetector.setLevel(settings.leakDetectionLevel.jResourceLeakDetectionLevel)
    }
  }
}
