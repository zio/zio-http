package zhttp.service

import io.netty.channel.{ChannelHandlerContext, EventLoopGroup => JEventLoopGroup}
import io.netty.util.concurrent.{EventExecutor => JEventExecutor}
import zio.internal.Executor
import zio.{Exit, Runtime, URIO, ZIO}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext => JExecutionContext}
import scala.jdk.CollectionConverters._

/**
 * Provides basic ZIO based utilities for any ZIO based program to execute in a channel's context. It will automatically
 * cancel the execution when the channel closes.
 */
final class UnsafeChannelExecutor[R](runtime: zio.Runtime[R], group: JEventLoopGroup) {
  private val localRuntime: mutable.Map[JEventExecutor, Runtime[R]] = {
    val map = mutable.Map.empty[JEventExecutor, Runtime[R]]

    for (exe <- group.asScala)
      map += exe -> runtime.withYieldOnStart(false).withExecutor {
        Executor.fromExecutionContext(runtime.platform.executor.yieldOpCount) {
          JExecutionContext.fromExecutor(exe)
        }
      }

    map
  }

  def unsafeExecute_(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {
    unsafeExecute(ctx, program) {
      case Exit.Success(_)     => ()
      case Exit.Failure(cause) =>
        cause.failureOption match {
          case Some(error: Throwable) => ctx.fireExceptionCaught(error)
          case _                      => ()
        }
        ctx.close()
    }
  }

  def unsafeExecute[E, A](ctx: ChannelHandlerContext, program: ZIO[R, E, A])(
    cb: Exit[E, A] => Any,
  ): Unit = {
    val rtm = localRuntime.getOrElse(ctx.executor(), runtime)
    rtm
      .unsafeRunAsync(for {
        fiber  <- program.fork
        _      <- ZIO.effectTotal {
          ctx.channel.closeFuture.addListener((_: AnyRef) => rtm.unsafeRunAsync_(fiber.interrupt): Unit)
        }
        result <- fiber.join
      } yield result)(cb)

  }
}

object UnsafeChannelExecutor {
  def make[R](group: JEventLoopGroup): URIO[R, UnsafeChannelExecutor[R]] =
    ZIO.runtime.map(runtime => new UnsafeChannelExecutor[R](runtime, group))
}
