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
import zio.http.netty.server.NettyDriver
trait Driver {
  def start(implicit trace: Trace): RIO[Scope, StartResult]

  def addApp[R](newApp: HttpApp[R], env: ZEnvironment[R])(implicit trace: Trace): UIO[Unit]

  def createClientDriver()(implicit trace: Trace): ZIO[Scope, Throwable, ClientDriver]
}

object Driver {
  final case class StartResult(port: Int, inFlightRequests: LongAdder)

  val default: ZLayer[Server.Config, Throwable, Driver] =
    NettyDriver.live
}
