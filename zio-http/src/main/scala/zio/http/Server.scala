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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.Server.ErrorCallback
import zio.http.netty.NettyServerConfig
import zio.http.netty.server._

trait Server {
  def install[R](httpApp: App[R], errorCallback: Option[ErrorCallback] = None)(implicit
    trace: Trace,
  ): URIO[R, Unit]

  def port: Int

}

object Server {

  type ErrorCallback = Cause[Nothing] => ZIO[Any, Nothing, Unit]
  def serve[R](
    httpApp: App[R],
    errorCallback: Option[ErrorCallback] = None,
  )(implicit trace: Trace): URIO[R with Server, Nothing] =
    install(httpApp, errorCallback) *> ZIO.never

  def install[R](
    httpApp: App[R],
    errorCallback: Option[ErrorCallback] = None,
  )(implicit trace: Trace): URIO[R with Server, Int] = {
    ZIO.serviceWithZIO[Server](_.install(httpApp, errorCallback)) *> ZIO.service[Server].map(_.port)
  }

  def defaultWithPort(port: Int)(implicit trace: Trace): ZLayer[Any, Throwable, Server] =
    defaultWith(_.port(port))

  def defaultWith(f: ServerConfig => ServerConfig)(implicit trace: Trace): ZLayer[Any, Throwable, Server] =
    live(f(ServerConfig.default))

  val default: ZLayer[Any, Throwable, Server] = {
    implicit val trace = Trace.empty
    ServerConfig.live >>> live
  }

  def live(conf: ServerConfig)(implicit trace: Trace): ZLayer[Any, Throwable, Server] = {
    ServerConfig.live(conf) >>> Server.live
  }

  val base: ZLayer[Driver, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.scoped {
      for {
        driver <- ZIO.service[Driver]
        port   <- driver.start
      } yield ServerLive(driver, port)
    }
  }

  val live: ZLayer[ServerConfig, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty
    NettyDriver.default >>> base
  }

  val customized: ZLayer[ServerConfig & NettyServerConfig, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty
    NettyDriver.customized >>> base
  }

  private final case class ServerLive(
    driver: Driver,
    bindPort: Int,
  ) extends Server {
    override def install[R](httpApp: App[R], errorCallback: Option[ErrorCallback])(implicit
      trace: Trace,
    ): URIO[R, Unit] =
      ZIO.environment[R].flatMap(driver.addApp(httpApp, _)) *> setErrorCallback(errorCallback)

    override def port: Int = bindPort

    private def setErrorCallback(errorCallback: Option[ErrorCallback])(implicit trace: Trace): UIO[Unit] =
      driver
        .setErrorCallback(errorCallback)
        .unless(errorCallback.isEmpty)
        .map(_.getOrElse(()))

  }

}
