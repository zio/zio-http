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
import zio.stacktracer.TracingImplicits.disableAutoTrace

import io.netty.channel._
import io.netty.util.concurrent.{Future, GenericFutureListener}

private[zio] final class NettyRuntime(zioRuntime: Runtime[Any]) {
  private[this] val rtm = zioRuntime.unsafe

  def run(
    ctx: ChannelHandlerContext,
    ensured: () => Unit,
    preferOnCurrentThread: Boolean,
  )(
    program: ZIO[Any, Throwable, Any],
  )(implicit unsafe: Unsafe, trace: Trace): Unit = {

    def onExit(exit: Exit[Throwable, Any]): Unit = {
      ensured()
      exit match {
        case Exit.Success(_)     =>
        case Exit.Failure(cause) =>
          cause.failureOption.orElse(cause.dieOption) match {
            case None        => ()
            case Some(error) => ctx.fireExceptionCaught(error)
          }
          if (ctx.channel().isOpen) ctx.close(): Unit
      }
    }

    def removeListener(close: GenericFutureListener[Future[_ >: Void]]): Unit =
      ctx.channel().closeFuture().removeListener(close): Unit

    val forkOrExit = if (preferOnCurrentThread) rtm.runOrFork(program) else Left(rtm.fork(program))

    forkOrExit match {
      case Left(fiber) =>
        // Close the connection if the program fails
        // When connection closes, interrupt the program
        val close = closeListener(fiber)
        ctx.channel().closeFuture.addListener(close)
        fiber.unsafe.addObserver { exit =>
          removeListener(close)
          onExit(exit)
        }
      case Right(exit) =>
        onExit(exit)
    }
  }

  @throws[Throwable]("Any errors that occur during the execution of the ZIO effect")
  def unsafeRunSync[A](program: ZIO[Any, Throwable, A])(implicit unsafe: Unsafe, trace: Trace): A =
    rtm.run(program).getOrThrowFiberFailure()

  private def closeListener(fiber: Fiber.Runtime[_, _])(implicit
    unsafe: Unsafe,
    trace: Trace,
  ): GenericFutureListener[Future[_ >: Void]] =
    (_: Future[_ >: Void]) => unsafeRunSync(ZIO.fiberIdWith(fiber.interruptAsFork))

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
