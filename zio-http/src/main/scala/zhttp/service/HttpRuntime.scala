package zhttp.service

import io.netty.channel.{ChannelHandlerContext, EventLoopGroup => JEventLoopGroup}
import io.netty.util.concurrent.{EventExecutor, Future, GenericFutureListener}
import zio._

import scala.concurrent.{ExecutionContext => JExecutionContext}
import scala.jdk.CollectionConverters._

/**
 * Provides basic ZIO based utilities for any ZIO based program to execute in a
 * channel's context. It will automatically cancel the execution when the
 * channel closes.
 */
final class HttpRuntime[+R](strategy: HttpRuntime.Strategy[R]) {
  private[zhttp] val log = HttpRuntime.log

  private def closeListener(rtm: Runtime[Any], fiber: Fiber.Runtime[_, _]): GenericFutureListener[Future[_ >: Void]] =
    (_: Future[_ >: Void]) =>
      Unsafe.unsafeCompat { implicit u =>
        rtm.unsafe.fork(fiber.interrupt)
        log.debug(s"Interrupted Fiber: [${fiber.id}]")
      }

  private def onFailure(ctx: ChannelHandlerContext, cause: Cause[Throwable]) = {
    cause.failureOption.orElse(cause.dieOption) match {
      case None        => ()
      case Some(error) =>
        log.error("HttpRuntimeException:" + cause.prettyPrint)
        ctx.fireExceptionCaught(error)
    }
    if (ctx.channel().isOpen) ctx.close()
    ()
  }

  def unsafeRun(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {

    val (_, rtm) = strategy.runtime(ctx)

    // Close the connection if the program fails
    // When connection closes, interrupt the program

    Unsafe.unsafeCompat { implicit u =>
      val fiber = rtm.unsafe.fork(
        program.fork.map(f => {
          val listener = closeListener(rtm, f)
          ctx.channel().closeFuture.addListener(listener)
        }),
      )

      fiber.unsafe.addObserver {
        case Exit.Success(_)     => ()
        case Exit.Failure(cause) => onFailure(ctx, cause)
      }
    }
  }

  def unsafeRunUninterruptible(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {
    val (_, rtm) = strategy.runtime(ctx)
    log.debug(s"Started Uninterruptible")
    val pgm      = program

    Unsafe.unsafeCompat { implicit u =>
      rtm.unsafe.fork(pgm).unsafe.addObserver { msg =>
        log.debug(s"Completed Uninterruptible: [${msg}]")
        msg match {
          case Exit.Success(_)     => ()
          case Exit.Failure(cause) => onFailure(ctx, cause)
        }
      }
    }
  }
}

object HttpRuntime {
  private[zhttp] val log = Log.withTags("HttpRuntime")

  def dedicated[R](group: JEventLoopGroup): URIO[R, HttpRuntime[R]] =
    Strategy.dedicated(group).map(runtime => new HttpRuntime[R](runtime))

  def default[R]: URIO[R, HttpRuntime[R]] =
    Strategy.default().map(runtime => new HttpRuntime[R](runtime))

  def sticky[R](group: JEventLoopGroup): URIO[R, HttpRuntime[R]] =
    Strategy.sticky(group).map(runtime => new HttpRuntime[R](runtime))

  sealed trait Strategy[R] {
    def runtime(ctx: ChannelHandlerContext): (Executor, Runtime[R])
  }

  object Strategy {

    def dedicated[R](group: JEventLoopGroup): ZIO[R, Nothing, Strategy[R]] = {
      val dedicatedExecutor = Executor.fromExecutionContext {
        JExecutionContext.fromExecutor(group)
      }
      ZIO.runtime[R].map(runtime => Dedicated(runtime, dedicatedExecutor))
    }

    def default[R](): ZIO[R, Nothing, Strategy[R]] =
      ZIO.executorWith { executor =>
        ZIO.runtime[R].map(runtime => Default(runtime, executor))
      }

    def sticky[R](group: JEventLoopGroup): ZIO[R, Nothing, Strategy[R]] =
      ZIO.runtime[R].flatMap { default =>
        ZIO
          .foreach(group.asScala) { eventLoop =>
            ZIO.runtime[R].map(runtime => eventLoop -> runtime)
          }
          .map(group => Group(default, group.toMap))
      }

    case class Default[R](runtime: Runtime[R], executor: Executor) extends Strategy[R] {
      override def runtime(ctx: ChannelHandlerContext): (Executor, Runtime[R]) = (executor, runtime)
    }

    case class Dedicated[R](runtime: Runtime[R], executor: Executor) extends Strategy[R] {
      override def runtime(ctx: ChannelHandlerContext): (Executor, Runtime[R]) = (executor, runtime)
    }

    case class Group[R](default: Runtime[R], group: Map[EventExecutor, Runtime[R]]) extends Strategy[R] {
      override def runtime(ctx: ChannelHandlerContext): (Executor, Runtime[R]) = {
        val zioExecutor = Executor.fromJavaExecutor(ctx.executor())
        group.get(ctx.executor()) match {
          case None     => (zioExecutor, default)
          case Some(rt) => (zioExecutor, rt)
        }
      }
    }
  }
}
