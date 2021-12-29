package zhttp.service

import io.netty.bootstrap.ServerBootstrap
import io.netty.util.ResourceLeakDetector
import zhttp.http.Http._
import zhttp.http.{Http, HttpApp}
import zhttp.service.server.ServerSSLHandler._
import zhttp.service.server._
import zio.{ZManaged, _}

import java.net.{InetAddress, InetSocketAddress}

sealed trait Server[-R, +E] { self =>

  import Server._

  def ++[R1 <: R, E1 >: E](other: Server[R1, E1]): Server[R1, E1] =
    Concat(self, other)

  private def settings[R1 <: R, E1 >: E](s: Config[R1, E1] = Config()): Config[R1, E1] = self match {
    case Concat(self, other)  => other.settings(self.settings(s))
    case LeakDetection(level) => s.copy(leakDetectionLevel = level)
    case MaxRequestSize(size) => s.copy(maxRequestSize = size)
    case Error(errorHandler)  => s.copy(error = Some(errorHandler))
    case Ssl(sslOption)       => s.copy(sslOption = sslOption)
    case App(app)             => s.copy(app = app)
    case Address(address)     => s.copy(address = address)
    case AcceptContinue       => s.copy(acceptContinue = true)
    case KeepAlive            => s.copy(keepAlive = true)
    case FlowControl          => s.copy(flowControl = false)
    case ConsolidateFlush     => s.copy(consolidateFlush = true)
  }

  def make(implicit ev: E <:< Throwable): ZManaged[R with EventLoopGroup with ServerChannelFactory, Throwable, Unit] =
    Server.make(self.asInstanceOf[Server[R, Throwable]])

  def start(implicit ev: E <:< Throwable): ZIO[R with EventLoopGroup with ServerChannelFactory, Throwable, Nothing] =
    make.useForever
}

object Server {
  private[zhttp] final case class Config[-R, +E](
    leakDetectionLevel: LeakDetectionLevel = LeakDetectionLevel.SIMPLE,
    maxRequestSize: Int = 4 * 1024, // 4 kilo bytes

    // TODO: add error handler
    error: Option[Throwable => ZIO[R, Nothing, Unit]] = None,
    sslOption: ServerSSLOptions = null,

    // TODO: move app out of settings
    app: HttpApp[R, E] = Http.empty,
    address: InetSocketAddress = new InetSocketAddress(8080),
    acceptContinue: Boolean = false,
    keepAlive: Boolean = false,
    consolidateFlush: Boolean = false,
    flowControl: Boolean = false,
  )

  private final case class Concat[R, E](self: Server[R, E], other: Server[R, E])      extends Server[R, E]
  private final case class LeakDetection(level: LeakDetectionLevel)                   extends UServer
  private final case class MaxRequestSize(size: Int)                                  extends UServer
  private final case class Error[R](errorHandler: Throwable => ZIO[R, Nothing, Unit]) extends Server[R, Nothing]
  private final case class Ssl(sslOptions: ServerSSLOptions)                          extends UServer
  private final case class Address(address: InetSocketAddress)                        extends UServer
  private final case class App[R, E](app: HttpApp[R, E])                              extends Server[R, E]
  private case object KeepAlive                                                       extends Server[Any, Nothing]
  private case object ConsolidateFlush                                                extends Server[Any, Nothing]
  private case object AcceptContinue                                                  extends UServer
  private case object FlowControl                                                     extends UServer

  def app[R, E](http: HttpApp[R, E]): Server[R, E]        = Server.App(http)
  def maxRequestSize(size: Int): UServer                  = Server.MaxRequestSize(size)
  def port(port: Int): UServer                            = Server.Address(new InetSocketAddress(port))
  def bind(port: Int): UServer                            = Server.Address(new InetSocketAddress(port))
  def bind(hostname: String, port: Int): UServer          = Server.Address(new InetSocketAddress(hostname, port))
  def bind(inetAddress: InetAddress, port: Int): UServer  = Server.Address(new InetSocketAddress(inetAddress, port))
  def bind(inetSocketAddress: InetSocketAddress): UServer = Server.Address(inetSocketAddress)
  def error[R](errorHandler: Throwable => ZIO[R, Nothing, Unit]): Server[R, Nothing] = Server.Error(errorHandler)
  def ssl(sslOptions: ServerSSLOptions): UServer                                     = Server.Ssl(sslOptions)
  def acceptContinue: UServer                                                        = Server.AcceptContinue
  def disableFlowControl: UServer                                                    = Server.FlowControl
  val disableLeakDetection: UServer  = LeakDetection(LeakDetectionLevel.DISABLED)
  val simpleLeakDetection: UServer   = LeakDetection(LeakDetectionLevel.SIMPLE)
  val advancedLeakDetection: UServer = LeakDetection(LeakDetectionLevel.ADVANCED)
  val paranoidLeakDetection: UServer = LeakDetection(LeakDetectionLevel.PARANOID)
  val keepAlive: UServer             = KeepAlive
  val consolidateFlush: UServer      = ConsolidateFlush

  /**
   * Launches the app on the provided port.
   */
  def start[R <: Has[_]](
    port: Int,
    http: HttpApp[R, Throwable],
  ): ZIO[R, Throwable, Nothing] =
    (Server.bind(port) ++ Server.app(http)).make
      .zipLeft(ZManaged.succeed(println(s"Server started on port: ${port}")))
      .useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def start[R <: Has[_]](
    address: InetAddress,
    port: Int,
    http: HttpApp[R, Throwable],
  ): ZIO[R, Throwable, Nothing] =
    (Server.app(http) ++ Server.bind(address, port)).make.useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def start[R <: Has[_]](
    socketAddress: InetSocketAddress,
    http: HttpApp[R, Throwable],
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
      zExec          <- HttpRuntime.default[R].toManaged_
      reqHandler      = settings.app.compile(zExec, settings, ServerTimeGenerator.make)
      init            = ServerChannelInitializer(zExec, settings, reqHandler)
      serverBootstrap = new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
      _ <- ChannelFuture.asManaged(serverBootstrap.childHandler(init).bind(settings.address))

    } yield {
      ResourceLeakDetector.setLevel(settings.leakDetectionLevel.jResourceLeakDetectionLevel)
    }
  }
}
