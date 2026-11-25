package zio.http

import zio._

import zio.http.ZClient.Config
import zio.http.internal.FetchDriver

/**
 * Platform-specific layers that create a `Client`, mixed into the [[ZClient]] companion object.
 */
trait ZClientPlatformSpecific {

  /**
   * A layer that creates a `Client` from a [[ZClient.Config]] in the environment. On the JVM, it uses the Netty driver with `NettyConfig.defaultWithFastShutdown` and `DnsResolver.default`. On Scala.js, it uses the Fetch API and is the same as `live`.
   */
  def fromConfig: ZLayer[ZClient.Config, Throwable, Client] =
    live

  /**
   * A layer that creates a `Client` from a [[ZClient.Config]] and a driver in the environment, and adds the `User-Agent` header `ZClient.defaultUAHeader` when `addUserAgentHeader` is true. On the JVM, the driver is a [[ClientDriver]], the layer also needs a [[DnsResolver]], and the connection pool is created from `connectionPool`. On Scala.js, the driver is a `ZClient.Driver[Any, Scope, Throwable]`.
   */
  def customized: ZLayer[Config with ZClient.Driver[Any, Scope, Throwable], Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.scoped {
      for {
        config <- ZIO.service[Config]
        driver <- ZIO.service[ZClient.Driver[Any, Scope, Throwable]]
        baseClient = ZClient.fromDriver(driver)
      } yield
        if (config.addUserAgentHeader)
          baseClient.addHeader(ZClient.defaultUAHeader)
        else
          baseClient
    }
  }

  /**
   * A layer that creates a `Client` from a [[ZClient.Config]] in the environment. The layer is fresh: each use builds a new client instead of sharing one. On the JVM, it uses the Netty driver and also needs a `NettyConfig` and a [[DnsResolver]]. On Scala.js, it uses the Fetch API.
   */
  def live: ZLayer[ZClient.Config, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    FetchDriver.live >>> customized
  }.fresh

  /**
   * A layer that reads a [[ZClient.Config]] with ZIO Config at `path` (by default `zio.http.client`) and creates a `Client` with `live`. A configuration error fails the layer with a `RuntimeException` whose message starts with "Configuration error:". On the JVM, the layer also reads a `NettyConfig` at the same path and needs a [[DnsResolver]].
   */
  def configured(
    path: NonEmptyChunk[String] = NonEmptyChunk("zio", "http", "client"),
  )(implicit trace: Trace): ZLayer[Any, Throwable, Client] =
    ZLayer(ZIO.config(Config.config.nested(path.head, path.tail: _*)))
      .mapError(error => new RuntimeException(s"Configuration error: $error")) >>> live

  /**
   * A layer that creates a `Client` with `ZClient.Config.default`, like `fromConfig`.
   */
  def default: ZLayer[Any, Throwable, Client] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(Config.default) >>> fromConfig
  }

}
