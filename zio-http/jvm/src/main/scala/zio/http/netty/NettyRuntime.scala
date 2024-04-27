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

import scala.annotation.unused

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import io.netty.channel._
import io.netty.util.concurrent.{Future, GenericFutureListener}

private[zio] final class NettyRuntime(zRuntime: Runtime[Any]) {

  def runtime(@unused ctx: ChannelHandlerContext): Runtime[Any] = zRuntime

  def run(ctx: ChannelHandlerContext, ensured: () => Unit, interruptOnClose: Boolean = true)(
    program: ZIO[Any, Throwable, Any],
  )(implicit unsafe: Unsafe, trace: Trace): Unit = {
    val rtm: Runtime[Any] = runtime(ctx)

    def onFailure(cause: Cause[Throwable], ctx: ChannelHandlerContext): Unit = {
      cause.failureOption.orElse(cause.dieOption) match {
        case None        => ()
        case Some(error) =>
          ctx.fireExceptionCaught(error)
      }
      if (ctx.channel().isOpen) ctx.close(): Unit
    }

    def removeListener(close: GenericFutureListener[Future[_ >: Void]]): Unit = {
      if (close ne null)
        ctx.channel().closeFuture().removeListener(close): Unit
    }

    // See https://github.com/zio/zio-http/pull/2782 on why forking is preferable over runOrFork
    val fiber = rtm.unsafe.fork(program)

    // Close the connection if the program fails
    // When connection closes, interrupt the program
    val close: GenericFutureListener[Future[_ >: Void]] =
      if (interruptOnClose) {
        val close0 = closeListener(rtm, fiber)
        ctx.channel().closeFuture.addListener(close0)
        close0
      } else null

    fiber.unsafe.addObserver {
      case Exit.Success(_)     =>
        removeListener(close)
        ensured()
      case Exit.Failure(cause) =>
        removeListener(close)
        onFailure(cause, ctx)
        ensured()
    }
  }

  def runUninterruptible(ctx: ChannelHandlerContext, ensured: () => Unit)(
    program: ZIO[Any, Throwable, Any],
  )(implicit unsafe: Unsafe, trace: Trace): Unit =
    run(ctx, ensured, interruptOnClose = false)(program)

  private def closeListener(rtm: Runtime[Any], fiber: Fiber.Runtime[_, _])(implicit
    trace: Trace,
  ): GenericFutureListener[Future[_ >: Void]] =
    (_: Future[_ >: Void]) => {
      val _ = rtm.unsafe.fork {
        fiber.interrupt
      }(implicitly[Trace], Unsafe.unsafe)
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
