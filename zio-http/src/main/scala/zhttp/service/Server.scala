package zhttp.service

import io.netty.bootstrap.ServerBootstrap
import io.netty.util.ResourceLeakDetector
import zhttp.http.{Status, _}
import zhttp.service.server.ServerSSLHandler._
import zhttp.service.server._
import zio.{ZManaged, _}

import java.net.{InetAddress, InetSocketAddress}

sealed trait Server[-R, +E] { self =>

  import Server._

  def ++[R1 <: R, E1 >: E](other: Server[R1, E1]): Server[R1, E1] =
    Concat(self, other)

  private def settings[R1 <: R, E1 >: E](s: Settings[R1, E1] = Settings()): Settings[R1, E1] = self match {
    case Concat(self, other)  => other.settings(self.settings(s))
    case LeakDetection(level) => s.copy(leakDetectionLevel = level)
    case App(http)            => s.copy(http = http)
    case MaxRequestSize(size) => s.copy(maxRequestSize = size)
    case Error(errorHandler)  => s.copy(error = Some(errorHandler))
    case Ssl(sslOption)       => s.copy(sslOption = sslOption)
    case Http2                => s.copy(enableHttp2 = true)
    case Address(address)     => s.copy(address = address)
  }

  def make(implicit ev: E <:< Throwable): ZManaged[R with EventLoopGroup with ServerChannelFactory, Throwable, Unit] =
    Server.make(self.asInstanceOf[Server[R, Throwable]])

  def start(implicit ev: E <:< Throwable): ZIO[R with EventLoopGroup with ServerChannelFactory, Throwable, Nothing] =
    make.useForever
}

object Server {
  private[zhttp] final case class Settings[-R, +E](
    http: HttpApp[R, E] = HttpApp.empty(Status.NOT_FOUND),
    leakDetectionLevel: LeakDetectionLevel = LeakDetectionLevel.SIMPLE,
    maxRequestSize: Int = 4 * 1024, // 4 kilo bytes
    error: Option[Throwable => ZIO[R, Nothing, Unit]] = None,
    sslOption: ServerSSLOptions = null,
    enableHttp2: Boolean = false,
    address: InetSocketAddress = new InetSocketAddress(8080),
  )

  private final case class Concat[R, E](self: Server[R, E], other: Server[R, E])      extends Server[R, E]
  private final case class LeakDetection(level: LeakDetectionLevel)                   extends UServer
  private final case class MaxRequestSize(size: Int)                                  extends UServer
  private final case class App[R, E](http: HttpApp[R, E])                             extends Server[R, E]
  private final case class Error[R](errorHandler: Throwable => ZIO[R, Nothing, Unit]) extends Server[R, Nothing]
  private final case class Ssl(sslOptions: ServerSSLOptions)                          extends UServer
  private case object Http2                                                           extends UServer
  private final case class Address(address: InetSocketAddress)                        extends UServer

  def app[R, E](http: HttpApp[R, E]): Server[R, E]        = Server.App(http)
  def maxRequestSize(size: Int): UServer                  = Server.MaxRequestSize(size)
  def port(port: Int): UServer                            = Server.Address(new InetSocketAddress(port))
  def bind(port: Int): UServer                            = Server.Address(new InetSocketAddress(port))
  def bind(hostname: String, port: Int): UServer          = Server.Address(new InetSocketAddress(hostname, port))
  def bind(inetAddress: InetAddress, port: Int): UServer  = Server.Address(new InetSocketAddress(inetAddress, port))
  def bind(inetSocketAddress: InetSocketAddress): UServer = Server.Address(inetSocketAddress)
  def error[R](errorHandler: Throwable => ZIO[R, Nothing, Unit]): Server[R, Nothing] = Server.Error(errorHandler)
  def ssl(sslOptions: ServerSSLOptions): UServer                                     = Server.Ssl(sslOptions)
  def http2: UServer                                                                 = Server.Http2
  val disableLeakDetection: UServer  = LeakDetection(LeakDetectionLevel.DISABLED)
  val simpleLeakDetection: UServer   = LeakDetection(LeakDetectionLevel.SIMPLE)
  val advancedLeakDetection: UServer = LeakDetection(LeakDetectionLevel.ADVANCED)
  val paranoidLeakDetection: UServer = LeakDetection(LeakDetectionLevel.PARANOID)

  /**
   * Launches the app on the provided port.
   */
  def start[R <: Has[_]](
    port: Int,
    http: RHttpApp[R],
  ): ZIO[R, Throwable, Nothing] =
    (Server.bind(port) ++ Server.app(http)).make.useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def start[R <: Has[_]](
    hostname: String,
    port: Int,
    http: RHttpApp[R],
  ): ZIO[R, Throwable, Nothing] =
    (Server.bind(hostname, port) ++ Server.app(http)).make.useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def start[R <: Has[_]](
    address: InetAddress,
    port: Int,
    http: RHttpApp[R],
  ): ZIO[R, Throwable, Nothing] =
    (Server.app(http) ++ Server.bind(address, port)).make.useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def start[R <: Has[_]](
    socketAddress: InetSocketAddress,
    http: RHttpApp[R],
  ): ZIO[R, Throwable, Nothing] =
    (Server.app(http) ++ Server.bind(socketAddress)).make.useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def make[R](
    server: Server[R, Throwable],
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory, Throwable, Unit] = {
    val settings = server.settings()
    for {
      channelFactory <- ZManaged.access[ServerChannelFactory](_.get)
      eventLoopGroup <- ZManaged.access[EventLoopGroup](_.get)
      zExec          <- UnsafeChannelExecutor.make[R](eventLoopGroup).toManaged_
      httpH           = ServerRequestHandler(zExec, settings)
      http2H          = Http2ServerRequestHandler(zExec, settings)
      init            = ServerChannelInitializer(httpH, http2H, settings)
      serverBootstrap = new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
      _ <- ChannelFuture.asManaged(serverBootstrap.childHandler(init).bind(settings.address))

    } yield {
      ResourceLeakDetector.setLevel(settings.leakDetectionLevel.jResourceLeakDetectionLevel)
    }
  }
}
