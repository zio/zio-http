package zhttp.service

import io.netty.channel.{ChannelHandlerContext => JChannelHandlerContext, EventLoopGroup => JEventLoopGroup}
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

trait UnsafeChannelExecutor[R] {
  def unsafeExecute_(ctx: JChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit
  def unsafeExecute[E, A](ctx: JChannelHandlerContext, program: ZIO[R, E, A])(cb: Exit[E, A] => Any): Unit
}

object UnsafeChannelExecutor {
  final class Default[R](map: mutable.Map[JEventExecutor, Runtime[R]], defaultRuntime: zio.Runtime[R])
      extends UnsafeChannelExecutor[R] {

    private def runtime(ctx: JChannelHandlerContext): Runtime[R] =
      map.getOrElse(ctx.executor(), defaultRuntime)

    override def unsafeExecute_(ctx: JChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {
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

    override def unsafeExecute[E, A](ctx: JChannelHandlerContext, program: ZIO[R, E, A])(
      cb: Exit[E, A] => Any,
    ): Unit = {
      val rtm = runtime(ctx)
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

  def make[R](group: JEventLoopGroup): URIO[R, UnsafeChannelExecutor[R]] =
    ZIO.runtime.map(runtime => {
      val map = mutable.Map.empty[JEventExecutor, Runtime[R]]
      for (exe <- group.asScala)
        map += exe -> runtime.withYieldOnStart(false).withExecutor {
          Executor.fromExecutionContext(runtime.platform.executor.yieldOpCount) {
            JExecutionContext.fromExecutor(exe)
          }
        }

      new Default[R](map, runtime)
    })

  def make[R]: URIO[R with EventLoopGroup, UnsafeChannelExecutor[R]] = for {
    eg  <- ZIO.access[EventLoopGroup](_.get)
    exe <- UnsafeChannelExecutor.make[R](eg)
  } yield exe
}
