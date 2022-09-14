package zio.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelPipeline
import io.netty.util.ResourceLeakDetector
import zio.http.Server2.ServerConfig.Config
import zio.http.service.ServerSSLHandler.ServerSSLOptions
import zio.http.service.{EventLoopGroup, ServerChannelFactory, _}
import zio.{ULayer, URIO, ZIO, ZLayer, durationInt}

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic.AtomicReference

object Server2 {

  trait Server {
    def serve[R](httpApp: HttpApp[R, Throwable]): URIO[R, Unit]
  }

  object Server {
    def serve[R](httpApp: HttpApp[R, Throwable]): URIO[R with Server, Unit] =
      ZIO.serviceWithZIO[Server2.Server](_.serve(httpApp)) *> ZIO.never

    val default = ServerConfig.default >>> live

    val live: ZLayer[Config with EventLoopGroup with ServerChannelFactory, Throwable, Server] = ZLayer.scoped {
      val instance = for {
        channelFactory <- ZIO.service[ServerChannelFactory]
        eventLoopGroup <- ZIO.service[EventLoopGroup]
        settings       <- ZIO.service[Config]
        rtm            <- HttpRuntime.sticky[Any](eventLoopGroup)
        time   = ServerTime.make(1000 millis)
        appRef = new AtomicReference[HttpApp[Any, Throwable]](Http.empty)
        reqHandler <- ZIO.succeed(ServerInboundHandler(appRef, rtm, settings, time))
        init            = ServerChannelInitializer(rtm, settings, reqHandler)
        serverBootstrap = new ServerBootstrap().channelFactory(channelFactory).group(eventLoopGroup)
        chf <- ZIO.attempt(serverBootstrap.childHandler(init).bind(settings.address))
        _   <- ChannelFuture.scoped(chf)
        _   <- ZIO.succeed(ResourceLeakDetector.setLevel(settings.leakDetectionLevel.jResourceLeakDetectionLevel))
      } yield ServerLive(appRef)

      instance

    }

    private final case class ServerLive(
      appRef: java.util.concurrent.atomic.AtomicReference[HttpApp[Any, Throwable]],
    ) extends Server {
      override def serve[R](httpApp: HttpApp[R, Throwable]): URIO[R, Unit] =
        ZIO.environment[R].map { env =>
          val newApp = httpApp.provideEnvironment(env)
          var loop   = true
          while (loop) {
            val oldApp = appRef.get()
            if (appRef.compareAndSet(oldApp, newApp ++ oldApp)) loop = false
          }
          ()
        }
    }

  }

  object ServerConfig {

    val default: ZLayer[Any, Nothing, Config with EventLoopGroup with ServerChannelFactory] = {
      val configLayer: ULayer[Config] = ZLayer.succeed(Config())
      configLayer ++ EventLoopGroup.auto(0) ++ ServerChannelFactory.auto
    }

    def live(config: Config): ZLayer[Any, Nothing, Config with EventLoopGroup with ServerChannelFactory] = {
      val (eventLoopGroupLayer, serverChannelFactoryLayer) = config.channelType match {
        case ChannelType.NIO    => (EventLoopGroup.nio(config.nThreads), ServerChannelFactory.nio)
        case ChannelType.EPOLL  => (EventLoopGroup.epoll(config.nThreads), ServerChannelFactory.epoll)
        case ChannelType.KQUEUE => (EventLoopGroup.kQueue(config.nThreads), ServerChannelFactory.kQueue)
        case ChannelType.URING  => (EventLoopGroup.uring(config.nThreads), ServerChannelFactory.uring)
        case ChannelType.AUTO   => (EventLoopGroup.auto(config.nThreads), ServerChannelFactory.auto)
      }
      val configLayer: ULayer[Config]                      = ZLayer.succeed(config)
      configLayer ++ eventLoopGroupLayer ++ serverChannelFactoryLayer
    }

    sealed trait ChannelType
    object ChannelType {
      case object NIO    extends ChannelType
      case object EPOLL  extends ChannelType
      case object KQUEUE extends ChannelType
      case object URING  extends ChannelType
      case object AUTO   extends ChannelType
    }

    sealed trait LeakDetectionLevel {
      self =>
      def jResourceLeakDetectionLevel: ResourceLeakDetector.Level = self match {
        case LeakDetectionLevel.DISABLED => ResourceLeakDetector.Level.DISABLED
        case LeakDetectionLevel.SIMPLE   => ResourceLeakDetector.Level.SIMPLE
        case LeakDetectionLevel.ADVANCED => ResourceLeakDetector.Level.ADVANCED
        case LeakDetectionLevel.PARANOID => ResourceLeakDetector.Level.PARANOID
      }
    }

    object LeakDetectionLevel {
      case object DISABLED extends LeakDetectionLevel

      case object SIMPLE extends LeakDetectionLevel

      case object ADVANCED extends LeakDetectionLevel

      case object PARANOID extends LeakDetectionLevel
    }

    final case class Config(
      leakDetectionLevel: LeakDetectionLevel = LeakDetectionLevel.SIMPLE,
      error: Option[Throwable => ZIO[Any, Nothing, Unit]] = None,
      sslOption: ServerSSLOptions = null,
      address: InetSocketAddress = new InetSocketAddress(8080),
      acceptContinue: Boolean = false,
      keepAlive: Boolean = true,
      consolidateFlush: Boolean = false,
      flowControl: Boolean = true,
      channelInitializer: ChannelPipeline => Unit = null,
      requestDecompression: (Boolean, Boolean) = (false, false),
      objectAggregator: Int = 1024 * 100,
      serverBootstrapInitializer: ServerBootstrap => Unit = null,
      channelType: ChannelType = ChannelType.AUTO,
      nThreads: Int = 0,
    ) {
      self =>
      def useAggregator: Boolean = objectAggregator >= 0

      /**
       * Creates a new server using a HttpServerExpectContinueHandler to send a
       * 100 HttpResponse if necessary.
       */
      def withAcceptContinue(enable: Boolean): Config = self.copy(acceptContinue = enable)

      /**
       * Creates a new server listening on the provided hostname and port.
       */
      def withBinding(hostname: String, port: Int): Config = self.copy(address = new InetSocketAddress(hostname, port))

      /**
       * Creates a new server listening on the provided InetAddress and port.
       */
      def withBinding(address: InetAddress, port: Int): Config =
        self.copy(address = new InetSocketAddress(address, port))

      /**
       * Creates a new server listening on the provided InetSocketAddress.
       */
      def withBinding(inetSocketAddress: InetSocketAddress): Config = self.copy(address = inetSocketAddress)

      /**
       * Creates a new server with FlushConsolidationHandler to control the
       * flush operations in a more efficient way if enabled (@see <a
       * href="https://netty.io/4.1/api/io/netty/handler/flush/FlushConsolidationHandler.html">FlushConsolidationHandler<a>).
       */
      def withConsolidateFlush(enable: Boolean): Config = self.copy(consolidateFlush = enable)

      /**
       * Creates a new server using netty FlowControlHandler if enable (@see <a
       * href="https://netty.io/4.1/api/io/netty/handler/flow/FlowControlHandler.html">FlowControlHandler</a>).
       */
      def withFlowControl(enable: Boolean): Config = self.copy(flowControl = enable)

      /**
       * Creates a new server with netty's HttpServerKeepAliveHandler to close
       * persistent connections when enable is true (@see <a
       * href="https://netty.io/4.1/api/io/netty/handler/codec/http/HttpServerKeepAliveHandler.html">HttpServerKeepAliveHandler</a>).
       */
      def withKeepAlive(enable: Boolean): Config = self.copy(keepAlive = enable)

      /**
       * Creates a new server with the leak detection level provided (@see <a
       * href="https://netty.io/4.1/api/io/netty/util/ResourceLeakDetector.Level.html">ResourceLeakDetector.Level</a>).
       */
      def withLeakDetection(level: LeakDetectionLevel): Config = self.copy(leakDetectionLevel = level)

      /**
       * Creates a new server with HttpObjectAggregator with the specified max
       * size of the aggregated content.
       */
      def withObjectAggregator(maxRequestSize: Int = 1024 * 100): Config =
        self.copy(objectAggregator = maxRequestSize)

      /**
       * Creates a new server listening on the provided port.
       */
      def withPort(port: Int): Config = self.copy(address = new InetSocketAddress(port))

      /**
       * Creates a new server with netty's HttpContentDecompressor to decompress
       * Http requests (@see <a href =
       * "https://netty.io/4.1/api/io/netty/handler/codec/http/HttpContentDecompressor.html">HttpContentDecompressor</a>).
       */
      def withRequestDecompression(enabled: Boolean, strict: Boolean): Config =
        self.copy(requestDecompression = (enabled, strict))

      /**
       * Creates a new server with the following ssl options.
       */
      def withSsl(sslOptions: ServerSSLOptions): Config = self.copy(sslOption = sslOptions)

      /**
       * Creates a new server by passing a function that modifies the channel
       * pipeline. This is generally not required as most of the features are
       * directly supported, however think of this as an escape hatch for more
       * advanced configurations that are not yet support by ZIO Http.
       *
       * NOTE: This method might be dropped in the future.
       */
      def withUnsafeChannelPipeline(unsafePipeline: ChannelPipeline => Unit): Config =
        self.copy(channelInitializer = unsafePipeline)

      /**
       * Provides unsafe access to netty's ServerBootstrap. Modifying server
       * bootstrap is generally not advised unless you know what you are doing.
       */
      def withUnsafeServerBootstrap(unsafeServerBootstrap: ServerBootstrap => Unit): Config =
        self.copy(serverBootstrapInitializer = unsafeServerBootstrap)

      def withMaxThreads(nThreads: Int): Config = self.copy(nThreads = nThreads)
    }
  }
}
