package zhttp.service

import io.netty.channel.{ChannelHandlerContext, EventLoopGroup => JEventLoopGroup}
import io.netty.util.concurrent.{Future, GenericFutureListener}
import zio._

/**
 * Provides basic ZIO based utilities for any ZIO based program to execute in a
 * channel's context. It will automatically cancel the execution when the
 * channel closes.
 */
final class HttpRuntime[+R](rtm: Runtime[R], strategy: HttpRuntime.Strategy) {
  private[zhttp] val log = HttpRuntime.log

  private def closeListener(rtm: Runtime[Any], fiber: Fiber.Runtime[_, _]): GenericFutureListener[Future[_ >: Void]] =
    (_: Future[_ >: Void]) =>
      Unsafe.unsafeCompat { implicit u =>
        val _ = rtm.unsafe.fork {
          fiber.interrupt.as(log.debug(s"Interrupted Fiber: [${fiber.id}]"))
        }
      }

  private def onFailure(ctx: ChannelHandlerContext, cause: Cause[Throwable]): Unit = {
    cause.failureOption.orElse(cause.dieOption) match {
      case None        => ()
      case Some(error) =>
        log.error("HttpRuntimeException:" + cause.prettyPrint)
        ctx.fireExceptionCaught(error)
    }
    if (ctx.channel().isOpen) ctx.close(): Unit
  }

  def unsafeRun(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any], interruptOnClose: Boolean = true): Unit = {
    val exe = strategy.executor(ctx)

    def removeListener(close: GenericFutureListener[Future[_ >: Void]]): Unit = {
      if (close != null)
        ctx.channel().closeFuture().removeListener(close): Unit
    }

    // Close the connection if the program fails
    // When connection closes, interrupt the program
    Unsafe.unsafeCompat { implicit u =>
      var close: GenericFutureListener[Future[_ >: Void]] = null

      val fiber = rtm.unsafe.fork(program.onExecutor(exe))

      log.debug(s"Started Fiber: [${fiber.id}]")
      if (interruptOnClose) {
        close = closeListener(rtm, fiber)
        ctx.channel().closeFuture.addListener(close)
      }

      fiber.unsafe.addObserver {
        case Exit.Success(_)     =>
          log.debug(s"Completed Fiber: [${fiber.id}]")
          removeListener(close)
        case Exit.Failure(cause) =>
          onFailure(ctx, cause)
          removeListener(close)
      }
    }
  }

  def unsafeRunUninterruptible(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit =
    unsafeRun(ctx)(program, interruptOnClose = false)
}

object HttpRuntime {
  private[zhttp] val log = Log.withTags("HttpRuntime")

  /**
   * Creates a runtime that uses a separate thread pool for ZIO operations.
   */
  def default[R]: URIO[R, HttpRuntime[R]] =
    for {
      rtm <- ZIO.runtime[R]
      exe <- ZIO.executor
    } yield new HttpRuntime(rtm, (ctx: Ctx) => exe)

  /**
   * Creates a runtime that uses the same thread that's used by the channel's
   * event loop. This should be the preferred way of creating the runtime for
   * the server.
   */
  def sticky[R](group: JEventLoopGroup): URIO[R, HttpRuntime[R]] =
    for {
      rtm <- ZIO.runtime[R]
    } yield new HttpRuntime(rtm, (ctx: Ctx) => Executor.fromJavaExecutor(ctx.executor))

  trait Strategy {
    def executor(ctx: ChannelHandlerContext): Executor
  }
}
