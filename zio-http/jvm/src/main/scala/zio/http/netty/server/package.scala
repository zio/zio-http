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

package zio.http.netty

import io.netty.channel.{ChannelFactory, EventLoopGroup, ServerChannel}
import zio._
import zio.http._

import java.util.concurrent.atomic.AtomicReference // scalafix:ok;
import zio.stacktracer.TracingImplicits.disableAutoTrace

package object server {
  private[server] type RoutesRef = AtomicReference[(Routes[Any, Response], Runtime[Any])]

  private[server] object AppRef {
    val empty: UIO[RoutesRef] = {
      implicit val trace: Trace = Trace.empty
      // Environment will be populated when we `install` the app
      ZIO.runtime[Any].map { rt =>
        // Request handlers are forked from this runtime (see `NettyRuntime.run`), and its runtime
        // flags are captured here, at layer-build time, then preserved by `NettyDriver.addApp`.
        // The `Interruption` flag must be forced on: if the `Server` layer happens to be built in
        // an uninterruptible region (e.g. from zio-test's `beforeAll`, which runs as an
        // `acquireRelease` acquire), every handler would otherwise inherit `Interruption = off` and
        // could never be interrupted. That silently defeats `NettyRuntime.closeListener`, which
        // interrupts the handler when the connection closes, so an in-flight handler keeps the
        // connection (and graceful shutdown) alive forever (#4240).
        val flags = RuntimeFlags.enable(rt.runtimeFlags)(RuntimeFlag.Interruption)
        new AtomicReference((Routes.empty, Runtime(ZEnvironment.empty, rt.fiberRefs, flags)))
      }
    }
  }

  val live: ZLayer[Server.Config, Throwable, Driver] =
    NettyDriver.live

  val manual
    : ZLayer[ServerEventLoopGroups & ChannelFactory[ServerChannel] & Server.Config & NettyConfig, Nothing, Driver] =
    NettyDriver.manual
}
