
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

import zio._

/**
 * Runs ZIO programs from Netty handlers
 */
final class NettyRuntime(val runtime: Runtime[Any]) {
  def run(zio: ZIO[Any, Throwable, Any])(ensuring: () => Unit): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.fork(zio.ensuring(ZIO.succeed(ensuring())))
    }

  def runUninterruptible(zio: ZIO[Any, Throwable, Any])(ensuring: () => Unit): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.fork(zio.uninterruptible.ensuring(ZIO.succeed(ensuring())))
    }
}

private[zio] object NettyRuntime {

  val noopEnsuring = () => ()

  /**
   * Runs ZIO programs from Netty handlers on the current ZIO runtime
   */
  val live: ZLayer[Any, Nothing, NettyRuntime] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.fromZIO {
      ZIO
        .runtime[Any]
        .map(new NettyRuntime(_))
    }
  }
}
