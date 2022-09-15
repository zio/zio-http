package zio.http

import io.netty.handler.codec.compression.{CompressionOptions => JCompressionOptions, StandardCompressionOptions}
import io.netty.util.ResourceLeakDetector
import zio.ZLayer
import zio.http.ServerConfig.{LeakDetectionLevel, ResponseCompressionConfig}
import zio.http.service.ServerSSLHandler.ServerSSLOptions

import java.net.{InetAddress, InetSocketAddress}

final case class ServerConfig(
  leakDetectionLevel: LeakDetectionLevel = LeakDetectionLevel.SIMPLE,
  sslOption: Option[ServerSSLOptions] = None,
  address: InetSocketAddress = new InetSocketAddress(8080),
  acceptContinue: Boolean = false,
  keepAlive: Boolean = true,
  consolidateFlush: Boolean = false,
  flowControl: Boolean = true,
  requestDecompression: (Boolean, Boolean) = (false, false),
  responseCompression: Option[ResponseCompressionConfig] = None,
  objectAggregator: Int = 1024 * 100,
  channelType: ChannelType = ChannelType.AUTO,
  nThreads: Int = 0,
) {
  self =>
  def useAggregator: Boolean = objectAggregator >= 0

  /**
   * Configure the server to use HttpServerExpectContinueHandler to send a 100
   * HttpResponse if necessary.
   */
  def acceptContinue(enable: Boolean): ServerConfig = self.copy(acceptContinue = enable)

  /**
   * Configure the server to listen on the provided hostname and port.
   */
  def binding(hostname: String, port: Int): ServerConfig =
    self.copy(address = new InetSocketAddress(hostname, port))

  /**
   * Configure the server to listen on the provided InetAddress and port.
   */
  def binding(address: InetAddress, port: Int): ServerConfig =
    self.copy(address = new InetSocketAddress(address, port))

  /**
   * Configure the server to listen on the provided InetSocketAddress.
   */
  def binding(inetSocketAddress: InetSocketAddress): ServerConfig = self.copy(address = inetSocketAddress)

  /**
   * Configure the server to use FlushConsolidationHandler to control the flush
   * operations in a more efficient way if enabled (@see <a
   * href="https://netty.io/4.1/api/io/netty/handler/flush/FlushConsolidationHandler.html">FlushConsolidationHandler<a>).
   */
  def consolidateFlush(enable: Boolean): ServerConfig = self.copy(consolidateFlush = enable)

  /**
   * Configure the server to use netty FlowControlHandler if enable (@see <a
   * href="https://netty.io/4.1/api/io/netty/handler/flow/FlowControlHandler.html">FlowControlHandler</a>).
   */
  def flowControl(enable: Boolean): ServerConfig = self.copy(flowControl = enable)

  /**
   * Configure the server to use netty's HttpServerKeepAliveHandler to close
   * persistent connections when enable is true (@see <a
   * href="https://netty.io/4.1/api/io/netty/handler/codec/http/HttpServerKeepAliveHandler.html">HttpServerKeepAliveHandler</a>).
   */
  def keepAlive(enable: Boolean): ServerConfig = self.copy(keepAlive = enable)

  /**
   * Configure the server to use the leak detection level provided (@see <a
   * href="https://netty.io/4.1/api/io/netty/util/ResourceLeakDetector.Level.html">ResourceLeakDetector.Level</a>).
   */
  def leakDetection(level: LeakDetectionLevel): ServerConfig = self.copy(leakDetectionLevel = level)

  /**
   * Configure the server to use HttpObjectAggregator with the specified max
   * size of the aggregated content.
   */
  def objectAggregator(maxRequestSize: Int = 1024 * 100): ServerConfig =
    self.copy(objectAggregator = maxRequestSize)

  /**
   * Configure the server to listen on the provided port.
   */
  def port(port: Int): ServerConfig = self.copy(address = new InetSocketAddress(port))

  /**
   * Configure the server to use netty's HttpContentDecompressor to decompress
   * Http requests (@see <a href =
   * "https://netty.io/4.1/api/io/netty/handler/codec/http/HttpContentDecompressor.html">HttpContentDecompressor</a>).
   */
  def requestDecompression(enabled: Boolean, strict: Boolean): ServerConfig =
    self.copy(requestDecompression = (enabled, strict))

  /**
   * Configure the new server with netty's HttpContentCompressor to compress
   * Http responses (@see <a href =
   * "https://netty.io/4.1/api/io/netty/handler/codec/http/HttpContentCompressor.html"HttpContentCompressor</a>).
   */
  def responseCompression(rCfg: ResponseCompressionConfig = ServerConfig.responseCompressionConfig()): ServerConfig =
    self.copy(responseCompression = Option(rCfg))

  /**
   * Configure the server with the following ssl options.
   */
  def ssl(sslOptions: ServerSSLOptions): ServerConfig = self.copy(sslOption = Some(sslOptions))

  /**
   * Configure the server to use a maximum of nThreads in to process requests.
   */
  def maxThreads(nThreads: Int): ServerConfig = self.copy(nThreads = nThreads)
}

object ServerConfig {
  val default: ServerConfig = ServerConfig()

  val live: ZLayer[Any, Nothing, ServerConfig] =
    ZLayer.succeed(ServerConfig.default)

  def live(config: ServerConfig): ZLayer[Any, Nothing, ServerConfig] = ZLayer.succeed(config)

  def responseCompressionConfig(
    contentThreshold: Int = 0,
    options: IndexedSeq[CompressionOptions] = IndexedSeq(CompressionOptions.gzip(), CompressionOptions.deflate()),
  ): ResponseCompressionConfig = ResponseCompressionConfig(contentThreshold, options)

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

  final case class ResponseCompressionConfig(
    contentThreshold: Int = 0,
    options: IndexedSeq[CompressionOptions] = IndexedSeq.empty,
  )

  final case class CompressionOptions(
    level: Int,
    bits: Int,
    mem: Int,
    kind: CompressionOptions.CompressionType,
  ) { self =>
    def toJava: JCompressionOptions = self.kind match {
      case CompressionOptions.GZip    => StandardCompressionOptions.gzip(self.level, self.bits, self.mem)
      case CompressionOptions.Deflate => StandardCompressionOptions.deflate(self.level, self.bits, self.mem)
    }
  }

  object CompressionOptions {
    val Level = 6
    val Bits  = 15
    val Mem   = 8

    /**
     * Creates GZip CompressionOptions. Defines defaults as per
     * io.netty.handler.codec.compression.GzipOptions#DEFAULT
     */
    def gzip(level: Int = Level, bits: Int = Bits, mem: Int = Mem): CompressionOptions =
      CompressionOptions(level, bits, mem, GZip)

    /**
     * Creates Deflate CompressionOptions. Defines defaults as per
     * io.netty.handler.codec.compression.DeflateOptions#DEFAULT
     */
    def deflate(level: Int = Level, bits: Int = Bits, mem: Int = Mem): CompressionOptions =
      CompressionOptions(level, bits, mem, Deflate)

    sealed trait CompressionType

    private case object GZip extends CompressionType

    private case object Deflate extends CompressionType
  }
}
