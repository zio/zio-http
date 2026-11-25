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

import java.util.concurrent.atomic.LongAdder

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.Driver.StartResult

/**
 * The low-level server engine behind a [[Server]]: it binds the listening
 * socket and dispatches requests to the installed routes. On the JVM the
 * implementation runs on Netty. Applications use [[Server]] and its layers;
 * `Driver` is the part a different server engine would replace.
 */
trait Driver {

  /**
   * Binds the server to the address in its `Server.Config` and starts accepting
   * connections. The returned [[Driver.StartResult]] holds the port that was
   * bound. Closing the scope closes the listening channel.
   */
  def start(implicit trace: Trace): RIO[Scope, StartResult]

  /**
   * Adds `newRoutes` to the routes the driver serves, together with the
   * environment they need. Routes added earlier stay in place.
   */
  def addApp[R](newRoutes: Routes[R, Response], env: ZEnvironment[R])(implicit trace: Trace): UIO[Unit]

  /**
   * Creates a [[ClientDriver]] that shares this driver's worker event loop
   * group, so that a client can run on the server's threads.
   */
  def createClientDriver()(implicit trace: Trace): ZIO[Scope, Throwable, ClientDriver]
}

/** The default driver layer and the result type of [[Driver#start]]. */
object Driver extends DriverPlatformSpecific {

  /**
   * The result of [[Driver#start]]: the port the server listens on, and a
   * counter of the requests that are being handled, which the server reads
   * during a graceful shutdown.
   */
  final case class StartResult(port: Int, inFlightRequests: LongAdder)
}
