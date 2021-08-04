package zhttp.service

import io.netty.channel.{ChannelHandlerContext, EventLoopGroup => JEventLoopGroup}
import io.netty.util.concurrent.EventExecutor
import zio.internal.Executor
import zio.{Exit, Runtime, URIO, ZIO}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext => JExecutionContext}
import scala.jdk.CollectionConverters._

/**
 * Provides basic ZIO based utilities for any ZIO based program to execute in a channel's context. It will automatically
 * cancel the execution when the channel closes.
 */
final class UnsafeChannelExecutor[R](runtime: UnsafeChannelExecutor.RuntimeMap[R]) {

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
    val rtm = runtime.getRuntime(ctx)
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
  sealed trait RuntimeMap[R] {
    def getRuntime(ctx: ChannelHandlerContext): Runtime[R]
  }

  object RuntimeMap {

    case class Default[R](runtime: Runtime[R]) extends RuntimeMap[R] {
      override def getRuntime(ctx: ChannelHandlerContext): Runtime[R] = runtime
    }

    case class Group[R](runtime: Runtime[R], group: JEventLoopGroup) extends RuntimeMap[R] {
      private val localRuntime: mutable.Map[EventExecutor, Runtime[R]] = {
        val map = mutable.Map.empty[EventExecutor, Runtime[R]]
        for (exe <- group.asScala)
          map += exe -> runtime.withYieldOnStart(false).withExecutor {
            Executor.fromExecutionContext(runtime.platform.executor.yieldOpCount) {
              JExecutionContext.fromExecutor(exe)
            }
          }

        map
      }

      override def getRuntime(ctx: ChannelHandlerContext): Runtime[R] =
        localRuntime.getOrElse(ctx.executor(), runtime)
    }

    def make[R](group: JEventLoopGroup): ZIO[R, Nothing, RuntimeMap[R]] =
      ZIO.runtime[R].map(runtime => Group(runtime, group))

    def make[R](): ZIO[R, Nothing, RuntimeMap[R]] =
      ZIO.runtime[R].map(runtime => Default(runtime))
  }

  def make[R](group: JEventLoopGroup): URIO[R, UnsafeChannelExecutor[R]] =
    RuntimeMap.make(group).map(runtime => new UnsafeChannelExecutor[R](runtime))

  def make[R](): URIO[R, UnsafeChannelExecutor[R]] =
    RuntimeMap.make().map(runtime => new UnsafeChannelExecutor[R](runtime))
}
