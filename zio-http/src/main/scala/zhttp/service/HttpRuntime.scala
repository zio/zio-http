package zhttp.service

import io.netty.channel.{ChannelHandlerContext, EventLoopGroup => JEventLoopGroup}
import io.netty.util.concurrent.{EventExecutor, Future, GenericFutureListener}
import zio._
import zio.internal.Executor

import scala.collection.mutable
import scala.concurrent.{ExecutionContext => JExecutionContext}
import scala.jdk.CollectionConverters._

/**
 * Provides basic ZIO based utilities for any ZIO based program to execute in a
 * channel's context. It will automatically cancel the execution when the
 * channel closes.
 */
final class HttpRuntime[+R](strategy: HttpRuntime.Strategy[R]) {

  private def closeListener(rtm: Runtime[Any], fiber: Fiber.Runtime[_, _]): GenericFutureListener[Future[_ >: Void]] =
    (_: Future[_ >: Void]) => rtm.unsafeRunAsync_(fiber.interrupt): Unit

  private def onFailure(ctx: ChannelHandlerContext, cause: Cause[Throwable]) = {
    cause.failureOption.orElse(cause.dieOption) match {
      case None    => ()
      case Some(_) => Log.error("HttpRuntimeException:" + cause.prettyPrint)
    }
    if (ctx.channel().isOpen) ctx.close()
  }

  def unsafeRun(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {
    val rtm = strategy.runtime(ctx)

    // Close the connection if the program fails
    // When connection closes, interrupt the program

    rtm
      .unsafeRunAsync(for {
        fiber <- program.fork
        close <- UIO {
          val close = closeListener(rtm, fiber)
          ctx.channel().closeFuture.addListener(close)
          close
        }
        _     <- fiber.join
        _     <- UIO(ctx.channel().closeFuture().removeListener(close))
      } yield ()) {
        case Exit.Success(_)     => ()
        case Exit.Failure(cause) => onFailure(ctx, cause)
      }
  }

  def unsafeRunUninterruptible(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {
    val rtm = strategy.runtime(ctx)

    rtm
      .unsafeRunAsync(program) {
        case Exit.Success(_)     => ()
        case Exit.Failure(cause) => onFailure(ctx, cause)

      }
  }
}

object HttpRuntime {
  def dedicated[R](group: JEventLoopGroup): URIO[R, HttpRuntime[R]] =
    Strategy.dedicated(group).map(runtime => new HttpRuntime[R](runtime))

  def default[R]: URIO[R, HttpRuntime[R]] =
    Strategy.default().map(runtime => new HttpRuntime[R](runtime))

  def sticky[R](group: JEventLoopGroup): URIO[R, HttpRuntime[R]] =
    Strategy.sticky(group).map(runtime => new HttpRuntime[R](runtime))

  sealed trait Strategy[R] {
    def runtime(ctx: ChannelHandlerContext): Runtime[R]
  }

  object Strategy {

    def dedicated[R](group: JEventLoopGroup): ZIO[R, Nothing, Strategy[R]] =
      ZIO.runtime[R].map(runtime => Dedicated(runtime, group))

    def default[R](): ZIO[R, Nothing, Strategy[R]] =
      ZIO.runtime[R].map(runtime => Default(runtime))

    def sticky[R](group: JEventLoopGroup): ZIO[R, Nothing, Strategy[R]] =
      ZIO.runtime[R].map(runtime => Group(runtime, group))

    case class Default[R](runtime: Runtime[R]) extends Strategy[R] {
      override def runtime(ctx: ChannelHandlerContext): Runtime[R] = runtime
    }

    case class Dedicated[R](runtime: Runtime[R], group: JEventLoopGroup) extends Strategy[R] {
      private val localRuntime: Runtime[R] = runtime.withYieldOnStart(false).withExecutor {
        Executor.fromExecutionContext(runtime.platform.executor.yieldOpCount) {
          JExecutionContext.fromExecutor(group)
        }
      }

      override def runtime(ctx: ChannelHandlerContext): Runtime[R] = localRuntime
    }

    case class Group[R](runtime: Runtime[R], group: JEventLoopGroup) extends Strategy[R] {
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

      override def runtime(ctx: ChannelHandlerContext): Runtime[R] =
        localRuntime.getOrElse(ctx.executor(), runtime)
    }
  }
}
