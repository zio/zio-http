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

import zio._

import zio.http.Server.Config
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver

/**
 * Provides Netty-backed implementations of [[Server]] and [[Driver]].
 *
 * Use these layers when building a JVM server backed by Netty. Example:
 * {{{
 * Server.serve(app).provide(NettyServer.live, ZLayer.succeed(Server.Config.default))
 * }}}
 */
object NettyServer {

  /**
   * A [[Driver]] layer backed by the default Netty configuration.
   */
  val driver: ZLayer[Server.Config, Throwable, Driver] =
    NettyDriver.live

  /**
   * A [[Server]] layer using the default [[NettyConfig]] derived from
   * [[Server.Config]].
   */
  val live: ZLayer[Config, Throwable, Server with Driver] = {
    // tmp val needed for Scala 2
    val tmp: ZLayer[Driver & Config, Throwable, Server] = ZLayer.suspend(Server.base)
    ZLayer.makeSome[Config, Server with Driver](
      NettyDriver.live,
      tmp,
    )
  }

  /**
   * A [[Server]] layer using default [[Config]] and default [[NettyConfig]].
   * Equivalent to `Server.default` in the combined artifact.
   */
  val default: ZLayer[Any, Throwable, Server with Driver] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.succeed(Config.default) >>> live
  }

  /**
   * A [[Server]] layer using default [[Config]] with the given port.
   * Equivalent to `Server.defaultWithPort(port)` in the combined artifact.
   */
  def defaultWithPort(port: Int)(implicit trace: Trace): ZLayer[Any, Throwable, Server with Driver] =
    ZLayer.succeed(Config.default.port(port)) >>> live

  /**
   * A [[Server]] layer using a customised [[NettyConfig]].
   */
  val customized: ZLayer[Config & NettyConfig, Throwable, Driver with Server] = {
    // tmp val needed for Scala 2
    val tmp: ZLayer[Driver & Config, Throwable, Server] = ZLayer.suspend(Server.base)
    ZLayer.makeSome[Config & NettyConfig, Driver with Server](
      NettyDriver.customized,
      tmp,
    )
  }

}
