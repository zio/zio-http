package zhttp.service

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelPipeline
import io.netty.util.ResourceLeakDetector
import zhttp.http.Http._
import zhttp.http.{Http, HttpApp}
import zhttp.service.server.ServerSSLHandler._
import zhttp.service.server._
import zio.{ZManaged, _}

import java.net.{InetAddress, InetSocketAddress}

sealed trait Server[-R, +E] { self =>

  import Server._

  private def settings[R1 <: R, E1 >: E](s: Config[R1, E1] = Config()): Config[R1, E1] = self match {
    case Concat(self, other)                   => other.settings(self.settings(s))
    case LeakDetection(level)                  => s.copy(leakDetectionLevel = level)
    case Error(errorHandler)                   => s.copy(error = Some(errorHandler))
    case Ssl(sslOption)                        => s.copy(sslOption = sslOption)
    case App(app)                              => s.copy(app = app)
    case Address(address)                      => s.copy(address = address)
    case AcceptContinue(enabled)               => s.copy(acceptContinue = enabled)
    case KeepAlive(enabled)                    => s.copy(keepAlive = enabled)
    case FlowControl(enabled)                  => s.copy(flowControl = enabled)
    case ConsolidateFlush(enabled)             => s.copy(consolidateFlush = enabled)
    case UnsafeChannelPipeline(init)           => s.copy(channelInitializer = init)
    case RequestDecompression(enabled, strict) => s.copy(requestDecompression = (enabled, strict))
    case ObjectAggregator(maxRequestSize)      => s.copy(objectAggregator = maxRequestSize)
    case UnsafeServerBootstrap(init)           => s.copy(serverBootstrapInitializer = init)
  }

  def ++[R1 <: R, E1 >: E](other: Server[R1, E1]): Server[R1, E1] =
    Concat(self, other)

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
  def startDefault[R1 <: Has[_] with R](implicit ev: E <:< Throwable): ZIO[R1, Throwable, Nothing] =
    start.provideSomeLayer[R1](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  /**
   * Creates a new server using a HttpServerExpectContinueHandler to send a 100
   * HttpResponse if necessary.
   */
  def withAcceptContinue(enable: Boolean): Server[R, E] = Concat(self, Server.AcceptContinue(enable))

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
   * Creates a new server with FlushConsolidationHandler to control the flush
   * operations in a more efficient way if enabled (@see <a
   * href="https://netty.io/4.1/api/io/netty/handler/flush/FlushConsolidationHandler.html">FlushConsolidationHandler<a>).
   */
  def withConsolidateFlush(enable: Boolean): Server[R, E] = Concat(self, ConsolidateFlush(enable))

  /**
   * Creates a new server with the errorHandler provided.
   */
  def withError[R1](errorHandler: Throwable => ZIO[R1, Nothing, Unit]): Server[R with R1, E] =
    Concat(self, Server.Error(errorHandler))

  /**
   * Creates a new server using netty FlowControlHandler if enable (@see <a
   * href="https://netty.io/4.1/api/io/netty/handler/flow/FlowControlHandler.html">FlowControlHandler</a>).
   */
  def withFlowControl(enable: Boolean): Server[R, E] = Concat(self, Server.FlowControl(enable))

  /**
   * Creates a new server with netty's HttpServerKeepAliveHandler to close
   * persistent connections when enable is true (@see <a
   * href="https://netty.io/4.1/api/io/netty/handler/codec/http/HttpServerKeepAliveHandler.html">HttpServerKeepAliveHandler</a>).
   */
  def withKeepAlive(enable: Boolean): Server[R, E] = Concat(self, KeepAlive(enable))

  /**
   * Creates a new server with the leak detection level provided (@see <a
   * href="https://netty.io/4.1/api/io/netty/util/ResourceLeakDetector.Level.html">ResourceLeakDetector.Level</a>).
   */
  def withLeakDetection(level: LeakDetectionLevel): Server[R, E] = Concat(self, LeakDetection(level))

  /**
   * Creates a new server with HttpObjectAggregator with the specified max size
   * of the aggregated content.
   */
  def withObjectAggregator(maxRequestSize: Int = Int.MaxValue): Server[R, E] =
    Concat(self, ObjectAggregator(maxRequestSize))

  /**
   * Creates a new server listening on the provided port.
   */
  def withPort(port: Int): Server[R, E] = Concat(self, Server.Address(new InetSocketAddress(port)))

  /**
   * Creates a new server with netty's HttpContentDecompressor to decompress
   * Http requests (@see <a href =
   * "https://netty.io/4.1/api/io/netty/handler/codec/http/HttpContentDecompressor.html">HttpContentDecompressor</a>).
   */
  def withRequestDecompression(enabled: Boolean, strict: Boolean): Server[R, E] =
    Concat(self, RequestDecompression(enabled, strict))

  /**
   * Creates a new server with the following ssl options.
   */
  def withSsl(sslOptions: ServerSSLOptions): Server[R, E] = Concat(self, Server.Ssl(sslOptions))

  /**
   * Creates a new server by passing a function that modifies the channel
   * pipeline. This is generally not required as most of the features are
   * directly supported, however think of this as an escape hatch for more
   * advanced configurations that are not yet support by ZIO Http.
   *
   * NOTE: This method might be dropped in the future.
   */
  def withUnsafeChannelPipeline(unsafePipeline: ChannelPipeline => Unit): Server[R, E] =
    Concat(self, UnsafeChannelPipeline(unsafePipeline))

  /**
   * Provides unsafe access to netty's ServerBootstrap. Modifying server
   * bootstrap is generally not advised unless you know what you are doing.
   */
  def withUnsafeServerBootstrap(unsafeServerbootstrap: ServerBootstrap => Unit): Server[R, E] =
    Concat(self, UnsafeServerBootstrap(unsafeServerbootstrap))
}
object Server {
  val disableFlowControl: UServer    = Server.FlowControl(false)
  val disableLeakDetection: UServer  = LeakDetection(LeakDetectionLevel.DISABLED)
  val simpleLeakDetection: UServer   = LeakDetection(LeakDetectionLevel.SIMPLE)
  val advancedLeakDetection: UServer = LeakDetection(LeakDetectionLevel.ADVANCED)
  val paranoidLeakDetection: UServer = LeakDetection(LeakDetectionLevel.PARANOID)
  val disableKeepAlive: UServer      = Server.KeepAlive(false)
  val consolidateFlush: UServer      = ConsolidateFlush(true)

  def acceptContinue: UServer = Server.AcceptContinue(true)

  def app[R, E](http: HttpApp[R, E]): Server[R, E] = Server.App(http)

  /**
   * Creates a server from a http app.
   */
  def apply[R, E](http: HttpApp[R, E]): Server[R, E] = Server.App(http)

  def bind(port: Int): UServer = Server.Address(new InetSocketAddress(port))

  def bind(hostname: String, port: Int): UServer = Server.Address(new InetSocketAddress(hostname, port))

  def bind(inetAddress: InetAddress, port: Int): UServer = Server.Address(new InetSocketAddress(inetAddress, port))

  def bind(inetSocketAddress: InetSocketAddress): UServer = Server.Address(inetSocketAddress)

  def enableObjectAggregator(maxRequestSize: Int = Int.MaxValue): UServer = ObjectAggregator(maxRequestSize)

  def error[R](errorHandler: Throwable => ZIO[R, Nothing, Unit]): Server[R, Nothing] = Server.Error(errorHandler)

  def make[R](
    server: Server[R, Throwable],
  ): ZManaged[R with EventLoopGroup with ServerChannelFactory, Throwable, Start] = {
    val settings = server.settings()
    for {
      channelFactory <- ZManaged.access[ServerChannelFactory](_.get)
      eventLoopGroup <- ZManaged.access[EventLoopGroup](_.get)
      zExec          <- HttpRuntime.sticky[R](eventLoopGroup).toManaged_
      handler         = new ServerResponseWriter(zExec, settings, ServerTime.make)
      reqHandler      = settings.app.compile(zExec, settings, handler)
      init            = ServerChannelInitializer(zExec, settings, reqHandler)
      serverBootstrap = new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
      chf  <- ZManaged.effect(serverBootstrap.childHandler(init).bind(settings.address))
      _    <- ChannelFuture.asManaged(chf)
      port <- ZManaged.effect(chf.channel().localAddress().asInstanceOf[InetSocketAddress].getPort)
    } yield {
      ResourceLeakDetector.setLevel(settings.leakDetectionLevel.jResourceLeakDetectionLevel)
      Start(port)
    }
  }

  def port(port: Int): UServer = Server.Address(new InetSocketAddress(port))

  def requestDecompression(strict: Boolean): UServer = Server.RequestDecompression(enabled = true, strict = strict)

  def ssl(sslOptions: ServerSSLOptions): UServer = Server.Ssl(sslOptions)

  /**
   * Launches the app on the provided port.
   */
  def start[R <: Has[_]](
    port: Int,
    http: HttpApp[R, Throwable],
  ): ZIO[R, Throwable, Nothing] = {
    Server(http)
      .withPort(port)
      .make
      .flatMap(start => ZManaged.succeed(Log.info(s"Server started on port: ${start.port}")))
      .useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)
  }

  def start[R <: Has[_]](
    address: InetAddress,
    port: Int,
    http: HttpApp[R, Throwable],
  ): ZIO[R, Throwable, Nothing] =
    Server(http)
      .withBinding(address, port)
      .make
      .useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def start[R <: Has[_]](
    socketAddress: InetSocketAddress,
    http: HttpApp[R, Throwable],
  ): ZIO[R, Throwable, Nothing] =
    Server(http)
      .withBinding(socketAddress)
      .make
      .useForever
      .provideSomeLayer[R](EventLoopGroup.auto(0) ++ ServerChannelFactory.auto)

  def unsafePipeline(pipeline: ChannelPipeline => Unit): UServer = UnsafeChannelPipeline(pipeline)

  def unsafeServerBootstrap(serverBootstrap: ServerBootstrap => Unit): UServer = UnsafeServerBootstrap(serverBootstrap)

  /**
   * Holds server start information.
   */
  final case class Start(port: Int = 0)

  private[zhttp] final case class Config[-R, +E](
    leakDetectionLevel: LeakDetectionLevel = LeakDetectionLevel.SIMPLE,
    error: Option[Throwable => ZIO[R, Nothing, Unit]] = None,
    sslOption: ServerSSLOptions = null,

    // TODO: move app out of settings
    app: HttpApp[R, E] = Http.empty,
    address: InetSocketAddress = new InetSocketAddress(8080),
    acceptContinue: Boolean = false,
    keepAlive: Boolean = true,
    consolidateFlush: Boolean = false,
    flowControl: Boolean = true,
    channelInitializer: ChannelPipeline => Unit = null,
    requestDecompression: (Boolean, Boolean) = (false, false),
    objectAggregator: Int = -1,
    serverBootstrapInitializer: ServerBootstrap => Unit = null,
  ) {
    def useAggregator: Boolean = objectAggregator >= 0
  }

  private final case class Concat[R, E](self: Server[R, E], other: Server[R, E]) extends Server[R, E]

  private final case class LeakDetection(level: LeakDetectionLevel) extends UServer

  private final case class Error[R](errorHandler: Throwable => ZIO[R, Nothing, Unit]) extends Server[R, Nothing]

  private final case class Ssl(sslOptions: ServerSSLOptions) extends UServer

  private final case class Address(address: InetSocketAddress) extends UServer

  private final case class App[R, E](app: HttpApp[R, E]) extends Server[R, E]

  private final case class KeepAlive(enabled: Boolean) extends Server[Any, Nothing]

  private final case class ConsolidateFlush(enabled: Boolean) extends Server[Any, Nothing]

  private final case class AcceptContinue(enabled: Boolean) extends UServer

  private final case class FlowControl(enabled: Boolean) extends UServer

  private final case class UnsafeChannelPipeline(init: ChannelPipeline => Unit) extends UServer

  private final case class RequestDecompression(enabled: Boolean, strict: Boolean) extends UServer

  private final case class ObjectAggregator(maxRequestSize: Int) extends UServer

  private final case class UnsafeServerBootstrap(init: ServerBootstrap => Unit) extends UServer
}
