package zio.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelPipeline
import zio.ZIO
import zio.http.service.ServerSSLHandler.ServerSSLOptions

import java.net.{InetAddress, InetSocketAddress}



  final case class ServerConfig private (
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
    def withAcceptContinue(enable: Boolean): ServerConfig = self.copy(acceptContinue = enable)

    /**
     * Creates a new server listening on the provided hostname and port.
     */
    def withBinding(hostname: String, port: Int): ServerConfig = self.copy(address = new InetSocketAddress(hostname, port))

    /**
     * Creates a new server listening on the provided InetAddress and port.
     */
    def withBinding(address: InetAddress, port: Int): ServerConfig =
      self.copy(address = new InetSocketAddress(address, port))

    /**
     * Creates a new server listening on the provided InetSocketAddress.
     */
    def withBinding(inetSocketAddress: InetSocketAddress): ServerConfig = self.copy(address = inetSocketAddress)

    /**
     * Creates a new server with FlushConsolidationHandler to control the
     * flush operations in a more efficient way if enabled (@see <a
     * href="https://netty.io/4.1/api/io/netty/handler/flush/FlushConsolidationHandler.html">FlushConsolidationHandler<a>).
     */
    def withConsolidateFlush(enable: Boolean): ServerConfig = self.copy(consolidateFlush = enable)

    /**
     * Creates a new server using netty FlowControlHandler if enable (@see <a
     * href="https://netty.io/4.1/api/io/netty/handler/flow/FlowControlHandler.html">FlowControlHandler</a>).
     */
    def withFlowControl(enable: Boolean): ServerConfig = self.copy(flowControl = enable)

    /**
     * Creates a new server with netty's HttpServerKeepAliveHandler to close
     * persistent connections when enable is true (@see <a
     * href="https://netty.io/4.1/api/io/netty/handler/codec/http/HttpServerKeepAliveHandler.html">HttpServerKeepAliveHandler</a>).
     */
    def withKeepAlive(enable: Boolean): ServerConfig = self.copy(keepAlive = enable)

    /**
     * Creates a new server with the leak detection level provided (@see <a
     * href="https://netty.io/4.1/api/io/netty/util/ResourceLeakDetector.Level.html">ResourceLeakDetector.Level</a>).
     */
    def withLeakDetection(level: LeakDetectionLevel): ServerConfig = self.copy(leakDetectionLevel = level)

    /**
     * Creates a new server with HttpObjectAggregator with the specified max
     * size of the aggregated content.
     */
    def withObjectAggregator(maxRequestSize: Int = 1024 * 100): ServerConfig =
      self.copy(objectAggregator = maxRequestSize)

    /**
     * Creates a new server listening on the provided port.
     */
    def withPort(port: Int): ServerConfig = self.copy(address = new InetSocketAddress(port))

    /**
     * Creates a new server with netty's HttpContentDecompressor to decompress
     * Http requests (@see <a href =
     * "https://netty.io/4.1/api/io/netty/handler/codec/http/HttpContentDecompressor.html">HttpContentDecompressor</a>).
     */
    def withRequestDecompression(enabled: Boolean, strict: Boolean): ServerConfig =
      self.copy(requestDecompression = (enabled, strict))

    /**
     * Creates a new server with the following ssl options.
     */
    def withSsl(sslOptions: ServerSSLOptions): ServerConfig = self.copy(sslOption = sslOptions)

    /**
     * Creates a new server by passing a function that modifies the channel
     * pipeline. This is generally not required as most of the features are
     * directly supported, however think of this as an escape hatch for more
     * advanced configurations that are not yet support by ZIO Http.
     *
     * NOTE: This method might be dropped in the future.
     */
    def withUnsafeChannelPipeline(unsafePipeline: ChannelPipeline => Unit): ServerConfig =
      self.copy(channelInitializer = unsafePipeline)

    /**
     * Provides unsafe access to netty's ServerBootstrap. Modifying server
     * bootstrap is generally not advised unless you know what you are doing.
     */
    def withUnsafeServerBootstrap(unsafeServerBootstrap: ServerBootstrap => Unit): ServerConfig =
      self.copy(serverBootstrapInitializer = unsafeServerBootstrap)

    def withMaxThreads(nThreads: Int): ServerConfig = self.copy(nThreads = nThreads)
  }

object ServerConfig {
  val default = ServerConfig()
}
