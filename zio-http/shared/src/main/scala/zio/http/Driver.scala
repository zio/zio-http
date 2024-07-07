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

trait Driver {
  def start(implicit trace: Trace): RIO[Scope, StartResult]

  def addApp[R](newApp: Routes[R, Response], env: ZEnvironment[R])(implicit trace: Trace): UIO[Unit]

  def createClientDriver()(implicit trace: Trace): ZIO[Scope, Throwable, ClientDriver]
}

object Driver extends DriverPlatformSpecific {
  final case class StartResult(port: Int, inFlightRequests: LongAdder)

  /* NOTE for developers: This FiberRef allows instrumentation of zio-http apps
   * via javaagent for the purposes of monitoring / tracing / etc. via 3rd party
   * tooling. Do not change the type as it might break the instrumentation.
   */
  final val defaultMiddleware: FiberRef[Option[HandlerAspect[Any, Unit]]] =
    FiberRef.unsafe.make(Option.empty[HandlerAspect[Any, Unit]])(Unsafe.unsafe)

  /**
   * Sets a middleware that will be applied to all routes by default. This
   * middleware runs at the outermost boundary of the application (i.e., applies
   * first to requests and last to responses).
   */
  def setDefaultMiddleware(middleware: HandlerAspect[Any, Unit])(implicit trace: Trace): UIO[Unit] =
    defaultMiddleware.set(Some(middleware))
}
