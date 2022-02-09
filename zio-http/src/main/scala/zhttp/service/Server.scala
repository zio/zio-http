package zhttp.service

import io.netty.bootstrap.ServerBootstrap
import io.netty.util.ResourceLeakDetector
import zhttp.http.Http._
import zhttp.http.{Http, HttpApp}
import zhttp.service.server.ServerSSLHandler._
import zhttp.service.server._
import zhttp.service.server.content.handlers.ServerResponseHandler
import zio.{ZManaged, _}

import java.net.{InetAddress, InetSocketAddress}

sealed trait Server[-R, +E] { self =>

  import Server._

  def ++[R1 <: R, E1 >: E](other: Server[R1, E1]): Server[R1, E1] =
    Concat(self, other)

  private def settings[R1 <: R, E1 >: E](s: Config[R1, E1] = Config()): Config[R1, E1] = self match {
    case Concat(self, other)       => other.settings(self.settings(s))
    case LeakDetection(level)      => s.copy(leakDetectionLevel = level)
    case MaxRequestSize(size)      => s.copy(maxRequestSize = size)
    case Error(errorHandler)       => s.copy(error = Some(errorHandler))
    case Ssl(sslOption)            => s.copy(sslOption = sslOption)
    case App(app)                  => s.copy(app = app)
    case Address(address)          => s.copy(address = address)
    case AcceptContinue(enabled)   => s.copy(acceptContinue = enabled)
    case KeepAlive(enabled)        => s.copy(keepAlive = enabled)
    case FlowControl(enabled)      => s.copy(flowControl = enabled)
    case ConsolidateFlush(enabled) => s.copy(consolidateFlush = enabled)
  }

  def make(implicit
    ev: E <:< Throwable,
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory, Throwable, Start] =
    Server.make(self.asInstanceOf[Server[R, Throwable]])

  def start(implicit ev: E <:< Throwable): ZIO[R with EventLoopGroup with ServerChannelFactory, Throwable, Nothing] =
    make.useForever

  /**
   * Launches the app with current settings: default EventLoopGroup (nThreads =
   * 0) and ServerChannelFactory.auto.
   */
  def startDefault[R1 <: R](implicit ev: E <:< Throwable): ZIO[R1, Throwable, Nothing] =
    start.provideSomeLayer[R1](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  /**
   * Creates a new server with the maximum size of the request specified in
   * bytes.
   */
  def withMaxRequestSize(size: Int): Server[R, E] = Concat(self, Server.MaxRequestSize(size))

  /**
   * Creates a new server listening on the provided port.
   */
  def withPort(port: Int): Server[R, E] = Concat(self, Server.Address(new InetSocketAddress(port)))

  /**
   * Creates a new server listening on the provided hostname and port.
   */
  def withBinding(hostname: String, port: Int): Server[R, E] =
    Concat(self, Server.Address(new InetSocketAddress(hostname, port)))

  /**
   * Creates a new server listening on the provided InetAddress and port.
   */
  def withBinding(address: InetAddress, port: Int): Server[R, E] =
    Concat(self, Server.Address(new InetSocketAddress(address, port)))

  /**
   * Creates a new server listening on the provided InetSocketAddress.
   */
  def withBinding(inetSocketAddress: InetSocketAddress): Server[R, E] = Concat(self, Server.Address(inetSocketAddress))

  /**
   * Creates a new server with the errorHandler provided.
   */
  def withError[R1](errorHandler: Throwable => ZIO[R1, Nothing, Unit]): Server[R with R1, E] =
    Concat(self, Server.Error(errorHandler))

  /**
   * Creates a new server with the following ssl options.
   */
  def withSsl(sslOptions: ServerSSLOptions): Server[R, E] = Concat(self, Server.Ssl(sslOptions))

  /**
   * Creates a new server using a HttpServerExpectContinueHandler to send a 100
   * HttpResponse if necessary.
   */
  def withAcceptContinue(enable: Boolean): Server[R, E] = Concat(self, Server.AcceptContinue(enable))

  /**
   * Creates a new server using netty FlowControlHandler if enable (@see <a
   * href="https://netty.io/4.1/api/io/netty/handler/flow/FlowControlHandler.html">FlowControlHandler</a>).
   */
  def withFlowControl(enable: Boolean): Server[R, E] = Concat(self, Server.FlowControl(enable))

  /**
   * Creates a new server with the leak detection level provided (@see <a
   * href="https://netty.io/4.1/api/io/netty/util/ResourceLeakDetector.Level.html">ResourceLeakDetector.Level</a>).
   */
  def withLeakDetection(level: LeakDetectionLevel): Server[R, E] = Concat(self, LeakDetection(level))

  /**
   * Creates a new server with netty's HttpServerKeepAliveHandler to close
   * persistent connections when enable is true (@see <a
   * href="https://netty.io/4.1/api/io/netty/handler/codec/http/HttpServerKeepAliveHandler.html">HttpServerKeepAliveHandler</a>).
   */
  def withKeepAlive(enable: Boolean): Server[R, E] = Concat(self, KeepAlive(enable))

  /**
   * Creates a new server with FlushConsolidationHandler to control the flush
   * operations in a more efficient way if enabled (@see <a
   * href="https://netty.io/4.1/api/io/netty/handler/flush/FlushConsolidationHandler.html">FlushConsolidationHandler<a>).
   */
  def withConsolidateFlush(enable: Boolean): Server[R, E] = Concat(self, ConsolidateFlush(enable))
}

object Server {
  private[zhttp] final case class Config[-R, +E](
    leakDetectionLevel: LeakDetectionLevel = LeakDetectionLevel.SIMPLE,
    maxRequestSize: Int = 4 * 1024, // 4 kilo bytes
    error: Option[Throwable => ZIO[R, Nothing, Unit]] = None,
    sslOption: ServerSSLOptions = null,

    // TODO: move app out of settings
    app: HttpApp[R, E] = Http.empty,
    address: InetSocketAddress = new InetSocketAddress(8080),
    acceptContinue: Boolean = false,
    keepAlive: Boolean = true,
    consolidateFlush: Boolean = false,
    flowControl: Boolean = true,
  )

  /**
   * Holds server start information.
   */
  final case class Start(port: Int = 0)

  private final case class Concat[R, E](self: Server[R, E], other: Server[R, E])      extends Server[R, E]
  private final case class LeakDetection(level: LeakDetectionLevel)                   extends UServer
  private final case class MaxRequestSize(size: Int)                                  extends UServer
  private final case class Error[R](errorHandler: Throwable => ZIO[R, Nothing, Unit]) extends Server[R, Nothing]
  private final case class Ssl(sslOptions: ServerSSLOptions)                          extends UServer
  private final case class Address(address: InetSocketAddress)                        extends UServer
  private final case class App[R, E](app: HttpApp[R, E])                              extends Server[R, E]
  private final case class KeepAlive(enabled: Boolean)                                extends Server[Any, Nothing]
  private final case class ConsolidateFlush(enabled: Boolean)                         extends Server[Any, Nothing]
  private final case class AcceptContinue(enabled: Boolean)                           extends UServer
  private final case class FlowControl(enabled: Boolean)                              extends UServer

  def app[R, E](http: HttpApp[R, E]): Server[R, E]        = Server.App(http)
  def maxRequestSize(size: Int): UServer                  = Server.MaxRequestSize(size)
  def port(port: Int): UServer                            = Server.Address(new InetSocketAddress(port))
  def bind(port: Int): UServer                            = Server.Address(new InetSocketAddress(port))
  def bind(hostname: String, port: Int): UServer          = Server.Address(new InetSocketAddress(hostname, port))
  def bind(inetAddress: InetAddress, port: Int): UServer  = Server.Address(new InetSocketAddress(inetAddress, port))
  def bind(inetSocketAddress: InetSocketAddress): UServer = Server.Address(inetSocketAddress)
  def error[R](errorHandler: Throwable => ZIO[R, Nothing, Unit]): Server[R, Nothing] = Server.Error(errorHandler)
  def ssl(sslOptions: ServerSSLOptions): UServer                                     = Server.Ssl(sslOptions)
  def acceptContinue: UServer                                                        = Server.AcceptContinue(true)
  val disableFlowControl: UServer                                                    = Server.FlowControl(false)
  val disableLeakDetection: UServer  = LeakDetection(LeakDetectionLevel.DISABLED)
  val simpleLeakDetection: UServer   = LeakDetection(LeakDetectionLevel.SIMPLE)
  val advancedLeakDetection: UServer = LeakDetection(LeakDetectionLevel.ADVANCED)
  val paranoidLeakDetection: UServer = LeakDetection(LeakDetectionLevel.PARANOID)
  val disableKeepAlive: UServer      = Server.KeepAlive(false)
  val consolidateFlush: UServer      = ConsolidateFlush(true)

  /**
   * Creates a server from a http app.
   */
  def apply[R, E](http: HttpApp[R, E]): Server[R, E] = Server.App(http)

  /**
   * Launches the app on the provided port.
   */
  def start[R](
    port: Int,
    http: HttpApp[R, Throwable],
  ): ZIO[R, Throwable, Nothing] = {
    (Server(http)
      .withPort(port))
      .make
      .flatMap(start => ZManaged.succeed(println(s"Server started on port: ${start.port}")))
      .useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)
  }

  def start[R](
    address: InetAddress,
    port: Int,
    http: HttpApp[R, Throwable],
  ): ZIO[R, Throwable, Nothing] =
    (Server(http)
      .withBinding(address, port))
      .make
      .useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def start[R](
    socketAddress: InetSocketAddress,
    http: HttpApp[R, Throwable],
  ): ZIO[R, Throwable, Nothing] =
    (Server(http)
      .withBinding(socketAddress))
      .make
      .useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def make[R](
    server: Server[R, Throwable],
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory, Throwable, Start] = {
    val settings = server.settings()
    for {
      channelFactory <- ZManaged.service[ServerChannelFactory]
      eventLoopGroup <- ZManaged.service[EventLoopGroup]
      zExec          <- HttpRuntime.default[R].toManaged
      reqHandler      = settings.app.compile(zExec, settings)
      respHandler     = ServerResponseHandler(zExec, settings, ServerTimeGenerator.make)
      init            = ServerChannelInitializer(zExec, settings, reqHandler, respHandler)
      serverBootstrap = new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
      chf  <- ZManaged.attempt(serverBootstrap.childHandler(init).bind(settings.address))
      _    <- ChannelFuture.asManaged(chf)
      port <- ZManaged.attempt(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
    } yield {
      ResourceLeakDetector.setLevel(settings.leakDetectionLevel.jResourceLeakDetectionLevel)
      Start(port)
    }
  }
}
