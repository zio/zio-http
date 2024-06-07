/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import java.net.{InetAddress, InetSocketAddress}
import java.util.concurrent.atomic._

import zio._

import zio.http.Server.Config.ResponseCompressionConfig

/**
 * Represents a server, which is capable of serving zero or more HTTP
 * applications.
 */
trait Server {

  /**
   * Installs the given HTTP application into the server.
   */
  @deprecated("Install Routes instead. Will be removed in the next release.", "3.0.0-RC7")
  def install[R](httpApp: HttpApp[R])(implicit trace: Trace, tag: EnvironmentTag[R]): URIO[R, Unit] =
    install(httpApp.routes)

  /**
   * Installs the given HTTP application into the server.
   */
  def install[R](httpApp: Routes[R, Response])(implicit trace: Trace, tag: EnvironmentTag[R]): URIO[R, Unit]

  /**
   * The port on which the server is listening.
   *
   * @return
   */
  def port: Int
}

object Server extends ServerPlatformSpecific {
  final case class Config(
    sslConfig: Option[SSLConfig],
    address: InetSocketAddress,
    acceptContinue: Boolean,
    keepAlive: Boolean,
    requestDecompression: Decompression,
    responseCompression: Option[ResponseCompressionConfig],
    requestStreaming: RequestStreaming,
    maxInitialLineLength: Int,
    maxHeaderSize: Int,
    logWarningOnFatalError: Boolean,
    gracefulShutdownTimeout: Duration,
    webSocketConfig: WebSocketConfig,
    idleTimeout: Option[Duration],
  ) {
    self =>

    /**
     * Configure the server to use HttpServerExpectContinueHandler to send a 100
     * HttpResponse if necessary.
     */
    def acceptContinue(enable: Boolean): Config = self.copy(acceptContinue = enable)

    /**
     * Configure the server to listen on the provided hostname and port.
     */
    def binding(hostname: String, port: Int): Config =
      self.copy(address = new InetSocketAddress(hostname, port))

    /**
     * Configure the server to listen on the provided InetAddress and port.
     */
    def binding(address: InetAddress, port: Int): Config =
      self.copy(address = new InetSocketAddress(address, port))

    /**
     * Configure the server to listen on the provided InetSocketAddress.
     */
    def binding(inetSocketAddress: InetSocketAddress): Config = self.copy(address = inetSocketAddress)

    /**
     * Disables streaming of request bodies. Payloads larger than
     * maxContentLength will be rejected
     */
    def disableRequestStreaming(maxContentLength: Int): Config =
      self.copy(requestStreaming = RequestStreaming.Disabled(maxContentLength))

    /** Enables streaming request bodies */
    def enableRequestStreaming: Config = self.copy(requestStreaming = RequestStreaming.Enabled)

    def gracefulShutdownTimeout(duration: Duration): Config = self.copy(gracefulShutdownTimeout = duration)

    def idleTimeout(duration: Duration): Config = self.copy(idleTimeout = Some(duration))

    /**
     * Configure the server to use netty's HttpServerKeepAliveHandler to close
     * persistent connections when enable is true (@see <a
     * href="https://netty.io/4.1/api/io/netty/handler/codec/http/HttpServerKeepAliveHandler.html">HttpServerKeepAliveHandler</a>).
     */
    def keepAlive(enable: Boolean): Config = self.copy(keepAlive = enable)

    /**
     * Log a warning in case of fatal errors when an error response cannot be
     * sent back to the client
     */
    def logWarningOnFatalError(enable: Boolean): Config = self.copy(logWarningOnFatalError = enable)

    def maxInitialLineLength(initialLineLength: Int): Config = self.copy(maxInitialLineLength = initialLineLength)

    /**
     * Configure the server to use `maxHeaderSize` value when encode/decode
     * headers.
     */
    def maxHeaderSize(headerSize: Int): Config = self.copy(maxHeaderSize = headerSize)

    def noIdleTimeout: Config = self.copy(idleTimeout = None)

    /**
     * Configure the server to listen on an available open port
     */
    def onAnyOpenPort: Config = port(0)

    /**
     * Configure the server to listen on the provided port.
     */
    def port(port: Int): Config = self.copy(address = new InetSocketAddress(port))

    /**
     * Configure the new server with netty's HttpContentCompressor to compress
     * Http responses (@see <a href =
     * "https://netty.io/4.1/api/io/netty/handler/codec/http/HttpContentCompressor.html"HttpContentCompressor</a>).
     */
    def responseCompression(rCfg: ResponseCompressionConfig = Config.ResponseCompressionConfig.default): Config =
      self.copy(responseCompression = Option(rCfg))

    /**
     * Configure the server to use netty's HttpContentDecompressor to decompress
     * Http requests (@see <a href =
     * "https://netty.io/4.1/api/io/netty/handler/codec/http/HttpContentDecompressor.html">HttpContentDecompressor</a>).
     */
    def requestDecompression(isStrict: Boolean): Config =
      self.copy(requestDecompression = if (isStrict) Decompression.Strict else Decompression.NonStrict)

    /**
     * Configure the server with the following ssl options.
     */
    def ssl(sslConfig: SSLConfig): Config = self.copy(sslConfig = Some(sslConfig))

    /** Enables or disables request body streaming */
    def requestStreaming(requestStreaming: RequestStreaming): Config =
      self.copy(requestStreaming = requestStreaming)

    def webSocketConfig(webSocketConfig: WebSocketConfig): Config =
      self.copy(webSocketConfig = webSocketConfig)
  }

  object Config {
    lazy val config: zio.Config[Config] = {
      SSLConfig.config.optional ++
        zio.Config.string("binding-host").optional ++
        zio.Config.int("binding-port").withDefault(Config.default.address.getPort) ++
        zio.Config.boolean("accept-continue").withDefault(Config.default.acceptContinue) ++
        zio.Config.boolean("keep-alive").withDefault(Config.default.keepAlive) ++
        Decompression.config.nested("request-decompression").withDefault(Config.default.requestDecompression) ++
        ResponseCompressionConfig.config.nested("response-compression").optional ++
        RequestStreaming.config.nested("request-streaming").withDefault(Config.default.requestStreaming) ++
        zio.Config.int("max-initial-line-length").withDefault(Config.default.maxInitialLineLength) ++
        zio.Config.int("max-header-size").withDefault(Config.default.maxHeaderSize) ++
        zio.Config.boolean("log-warning-on-fatal-error").withDefault(Config.default.logWarningOnFatalError) ++
        zio.Config.duration("graceful-shutdown-timeout").withDefault(Config.default.gracefulShutdownTimeout) ++
        zio.Config.duration("idle-timeout").optional.withDefault(Config.default.idleTimeout)
    }.map {
      case (
            sslConfig,
            host,
            port,
            acceptContinue,
            keepAlive,
            requestDecompression,
            responseCompression,
            requestStreaming,
            maxInitialLineLength,
            maxHeaderSize,
            logWarningOnFatalError,
            gracefulShutdownTimeout,
            idleTimeout,
          ) =>
        default.copy(
          sslConfig = sslConfig,
          address = new InetSocketAddress(host.getOrElse(Config.default.address.getHostName), port),
          acceptContinue = acceptContinue,
          keepAlive = keepAlive,
          requestDecompression = requestDecompression,
          responseCompression = responseCompression,
          requestStreaming = requestStreaming,
          maxInitialLineLength = maxInitialLineLength,
          maxHeaderSize = maxHeaderSize,
          logWarningOnFatalError = logWarningOnFatalError,
          gracefulShutdownTimeout = gracefulShutdownTimeout,
          idleTimeout = idleTimeout,
        )
    }

    lazy val default: Config = Config(
      sslConfig = None,
      address = new InetSocketAddress(8080),
      acceptContinue = false,
      keepAlive = true,
      requestDecompression = Decompression.No,
      responseCompression = None,
      requestStreaming = RequestStreaming.Disabled(1024 * 100),
      maxInitialLineLength = 4096,
      maxHeaderSize = 8192,
      logWarningOnFatalError = true,
      gracefulShutdownTimeout = 10.seconds,
      webSocketConfig = WebSocketConfig.default,
      idleTimeout = None,
    )

    final case class ResponseCompressionConfig(
      contentThreshold: Int,
      options: IndexedSeq[CompressionOptions],
    )

    object ResponseCompressionConfig {
      lazy val config: zio.Config[ResponseCompressionConfig] =
        (
          zio.Config.int("content-threshold").withDefault(ResponseCompressionConfig.default.contentThreshold) ++
            CompressionOptions.config.repeat.nested("options")
        ).map { case (contentThreshold, options) =>
          ResponseCompressionConfig(contentThreshold, options)
        }

      lazy val default: ResponseCompressionConfig =
        ResponseCompressionConfig(0, IndexedSeq(CompressionOptions.gzip(), CompressionOptions.deflate()))
    }

    sealed trait CompressionOptions {
      val name: String
    }

    object CompressionOptions {

      final case class GZip(cfg: DeflateConfig)    extends CompressionOptions { val name = "gzip"    }
      final case class Deflate(cfg: DeflateConfig) extends CompressionOptions { val name = "deflate" }
      final case class Brotli(cfg: BrotliConfig)   extends CompressionOptions { val name = "brotli"  }

      /**
       * @param level
       *   defines compression level, {@code 1} yields the fastest compression
       *   and {@code 9} yields the best compression. {@code 0} means no
       *   compression.
       * @param bits
       *   defines windowBits, The base two logarithm of the size of the history
       *   buffer. The value should be in the range {@code 9} to {@code 15}
       *   inclusive. Larger values result in better compression at the expense
       *   of memory usage
       * @param mem
       *   defines memlevel, How much memory should be allocated for the
       *   internal compression state. {@code 1} uses minimum memory and
       *   {@code 9} uses maximum memory. Larger values result in better and
       *   faster compression at the expense of memory usage
       */
      final case class DeflateConfig(
        level: Int,
        bits: Int,
        mem: Int,
      )

      object DeflateConfig {
        val DefaultLevel = 6
        val DefaultBits  = 15
        val DefaultMem   = 8
      }

      final case class BrotliConfig(
        quality: Int,
        lgwin: Int,
        mode: Mode,
      )

      object BrotliConfig {
        val DefaultQuality = 4
        val DefaultLgwin   = -1
        val DefaultMode    = Mode.Text
      }

      sealed trait Mode
      object Mode {
        case object Generic extends Mode
        case object Text    extends Mode
        case object Font    extends Mode

        def fromString(s: String): Mode = s.toLowerCase match {
          case "generic" => Generic
          case "text"    => Text
          case "font"    => Font
          case _         => Text
        }
      }

      /**
       * Creates GZip CompressionOptions. Defines defaults as per
       * io.netty.handler.codec.compression.GzipOptions#DEFAULT
       */
      def gzip(
        level: Int = DeflateConfig.DefaultLevel,
        bits: Int = DeflateConfig.DefaultBits,
        mem: Int = DeflateConfig.DefaultMem,
      ): CompressionOptions =
        CompressionOptions.GZip(DeflateConfig(level, bits, mem))

      /**
       * Creates Deflate CompressionOptions. Defines defaults as per
       * io.netty.handler.codec.compression.DeflateOptions#DEFAULT
       */
      def deflate(
        level: Int = DeflateConfig.DefaultLevel,
        bits: Int = DeflateConfig.DefaultBits,
        mem: Int = DeflateConfig.DefaultMem,
      ): CompressionOptions =
        CompressionOptions.Deflate(DeflateConfig(level, bits, mem))

      /**
       * Creates Brotli CompressionOptions. Defines defaults as per
       * io.netty.handler.codec.compression.BrotliOptions#DEFAULT
       */
      def brotli(
        quality: Int = BrotliConfig.DefaultQuality,
        lgwin: Int = BrotliConfig.DefaultLgwin,
        mode: Mode = BrotliConfig.DefaultMode,
      ): CompressionOptions =
        CompressionOptions.Brotli(BrotliConfig(quality, lgwin, mode))

      lazy val config: zio.Config[CompressionOptions] =
        (
          (zio.Config.int("level").withDefault(DeflateConfig.DefaultLevel) ++
            zio.Config.int("bits").withDefault(DeflateConfig.DefaultBits) ++
            zio.Config.int("mem").withDefault(DeflateConfig.DefaultMem)) ++
            zio.Config.int("quantity").withDefault(BrotliConfig.DefaultQuality) ++
            zio.Config.int("lgwin").withDefault(BrotliConfig.DefaultLgwin) ++
            zio.Config.string("mode").map(Mode.fromString).withDefault(BrotliConfig.DefaultMode) ++
            zio.Config.string("type")
        ).map { case (level, bits, mem, quantity, lgwin, mode, typ) =>
          typ.toLowerCase match {
            case "gzip"    => gzip(level, bits, mem)
            case "deflate" => deflate(level, bits, mem)
            case "brotli"  => brotli(quantity, lgwin, mode)
          }
        }
    }
  }

  sealed trait RequestStreaming

  object RequestStreaming {

    /** Enable streaming request bodies */
    case object Enabled extends RequestStreaming

    /**
     * Disable streaming request bodies. Bodies larger than the configured
     * maximum content length will be rejected.
     */
    final case class Disabled(maximumContentLength: Int) extends RequestStreaming

    lazy val config: zio.Config[RequestStreaming] =
      (zio.Config.boolean("enabled").withDefault(true) ++
        zio.Config.int("maximum-content-length").withDefault(1024 * 100)).map {
        case (true, _)          => Enabled
        case (false, maxLength) => Disabled(maxLength)
      }
  }

  @deprecated("Serve Routes instead. Will be removed in the next release.", "3.0.0-RC7")
  def serve[R](
    httpApp: HttpApp[R],
  )(implicit trace: Trace, tag: EnvironmentTag[R]): URIO[R with Server, Nothing] = {
    ZIO.logInfo("Starting the server...") *>
      install[R](httpApp) *>
      ZIO.logInfo("Server started") *>
      ZIO.never
  }

  @deprecated("Install Routes instead. Will be removed in the next release.", "3.0.0-RC7")
  def install[R](httpApp: HttpApp[R])(implicit trace: Trace, tag: EnvironmentTag[R]): URIO[R with Server, Int] = {
    ZIO.serviceWithZIO[Server](_.install[R](httpApp)) *> ZIO.serviceWith[Server](_.port)
  }

  def serve[R](
    httpApp: Routes[R, Response],
  )(implicit trace: Trace, tag: EnvironmentTag[R]): URIO[R with Server, Nothing] = {
    ZIO.logInfo("Starting the server...") *>
      ZIO.serviceWithZIO[Server](_.install[R](httpApp)) *>
      ZIO.logInfo("Server started") *>
      ZIO.never
  }

  def serve[R](
    route: Route[R, Response],
    routes: Route[R, Response]*,
  )(implicit trace: Trace, tag: EnvironmentTag[R]): URIO[R with Server, Nothing] = {
    serve(Routes(route, routes: _*))
  }

  def install[R](
    httpApp: Routes[R, Response],
  )(implicit trace: Trace, tag: EnvironmentTag[R]): URIO[R with Server, Int] = {
    ZIO.serviceWithZIO[Server](_.install[R](httpApp)) *> ZIO.serviceWith[Server](_.port)
  }

  private[http] val base: ZLayer[Driver & Config, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.scoped {
      for {
        driver           <- ZIO.service[Driver]
        config           <- ZIO.service[Config]
        inFlightRequests <- Promise.make[Throwable, LongAdder]
        _                <- Scope.addFinalizer(
          inFlightRequests.await.flatMap { counter =>
            ZIO
              .succeed(counter.longValue())
              .repeat(
                Schedule
                  .identity[Long]
                  .zip(Schedule.elapsed)
                  .untilOutput { case (inFlight, elapsed) =>
                    inFlight == 0L || elapsed > config.gracefulShutdownTimeout
                  } &&
                  Schedule.fixed(10.millis),
              )
          }.ignoreLogged,
        )
        result <- driver.start.catchAllCause(cause => inFlightRequests.failCause(cause) *> ZIO.refailCause(cause))
        _      <- inFlightRequests.succeed(result.inFlightRequests)
      } yield ServerLive(driver, result.port)
    }
  }

  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "server"),
  )(implicit trace: Trace): ZLayer[Any, Throwable, Server] =
    ZLayer(ZIO.config(Config.config.nested(path.head, path.tail: _*))).mapError(error =>
      new RuntimeException(s"Configuration error: $error"),
    ) >>> live

  def defaultWithPort(port: Int)(implicit trace: Trace): ZLayer[Any, Throwable, Server] =
    defaultWith(_.port(port))

  def defaultWith(f: Config => Config)(implicit trace: Trace): ZLayer[Any, Throwable, Server] =
    ZLayer.succeed(f(Config.default)) >>> live

  val default: ZLayer[Any, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(Config.default) >>> live
  }

  private final case class ServerLive(
    driver: Driver,
    bindPort: Int,
  ) extends Server {
    override def install[R](httpApp: Routes[R, Response])(implicit
      trace: Trace,
      tag: EnvironmentTag[R],
    ): URIO[R, Unit] =
      ZIO.environment[R].flatMap(env => driver.addApp(httpApp, env.prune[R]))

    override def port: Int = bindPort

  }
}
