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

import io.netty.channel._
import io.netty.util.concurrent.{Future, GenericFutureListener}

private[zio] trait NettyRuntime { self =>

  def runtime(ctx: ChannelHandlerContext): Runtime[Any]

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
      if (close != null)
        ctx.channel().closeFuture().removeListener(close): Unit
    }

    // Close the connection if the program fails
    // When connection closes, interrupt the program
    var close: GenericFutureListener[Future[_ >: Void]] = null

    rtm.unsafe.runOrFork(program) match {
      case Left(fiber) =>
        if (interruptOnClose) {
          close = closeListener(rtm, fiber)
          ctx.channel().closeFuture.addListener(close)
        }
        fiber.unsafe.addObserver {
          case Exit.Success(_)     =>
            removeListener(close)
            ensured()
          case Exit.Failure(cause) =>
            removeListener(close)
            onFailure(cause, ctx)
            ensured()
        }
      case Right(exit) =>
        ensured()
        exit match {
          case Exit.Success(_)     =>
          case Exit.Failure(cause) =>
            onFailure(cause, ctx)
        }
    }
  }

  def runUninterruptible(ctx: ChannelHandlerContext, ensured: () => Unit)(
    program: ZIO[Any, Throwable, Any],
  )(implicit unsafe: Unsafe, trace: Trace): Unit =
    run(ctx, ensured, interruptOnClose = false)(program)

  private def closeListener(rtm: Runtime[Any], fiber: Fiber.Runtime[_, _]): GenericFutureListener[Future[_ >: Void]] =
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
        .map { rtm =>
          new NettyRuntime {
            def runtime(ctx: ChannelHandlerContext): Runtime[Any] = rtm
          }
        }
    }
  }
}
