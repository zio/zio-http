package zhttp.service

import io.netty.channel.{ChannelHandlerContext, EventLoopGroup => JEventLoopGroup}
import io.netty.util.concurrent.{EventExecutor, Future, GenericFutureListener}
import zio._

import scala.collection.mutable
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

  def unsafeRun(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {

    val rtm = strategy.runtime(ctx)

    // Close the connection if the program fails
    // When connection closes, interrupt the program
    Unsafe.unsafe { implicit u =>
      rtm.unsafe.fork {
        for {
          fiber <- program.fork
          close <- ZIO.succeed {
            val close = closeListener(rtm, fiber)
            ctx.channel().closeFuture.addListener(close)
            close
          }
          _     <- fiber.join
          _     <- ZIO.succeed {
            ctx.channel().closeFuture().removeListener(close)
          }
        } yield ()
      }.unsafe.addObserver {
        case Exit.Success(_)     => ()
        case Exit.Failure(cause) => onFailure(ctx, cause)
      }
    }
  }

  def unsafeRunUninterruptible(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {
    val rtm = strategy.runtime(ctx)
    log.debug(s"Started Uninterruptible")

    Unsafe.unsafeCompat { implicit u =>
      rtm.unsafe.fork(program).unsafe.addObserver { msg =>
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

  /**
   * Creates a rutime that uses a separate thread pool for ZIO operations.
   */
  def default[R]: URIO[R, HttpRuntime[R]] =
    Strategy.default[R].map(runtime => new HttpRuntime[R](runtime))

  /**
   * Creates a runtime that uses the same thread that's used by the channel's
   * event loop. This should be the preferred way of creating the runtime for
   * the server.
   */
  def sticky[R](group: JEventLoopGroup): URIO[R, HttpRuntime[R]] =
    Strategy.sticky(group).map(runtime => new HttpRuntime[R](runtime))

  trait Strategy[R] {
    def runtime(ctx: ChannelHandlerContext): Runtime[R]
  }

  object Strategy {
    def default[R]: ZIO[R, Nothing, Strategy[R]] =
      ZIO.runtime[R].map { rtm =>
        new Strategy[R] {
          override def runtime(ctx: Ctx): Runtime[R] = rtm
        }
      }

    def sticky[R](group: JEventLoopGroup): ZIO[R, Nothing, Strategy[R]] = for {
      rtm <- ZIO.runtime[R]
      map <- ZIO.succeed(mutable.Map.empty[EventExecutor, Runtime[R]])
      _   <- ZIO.foreachDiscard(group.asScala) { eventLoop =>
        val executor  = Executor.fromJavaExecutor(eventLoop)
        val zExecutor = Runtime.setExecutor(executor)
        ZIO.runtime[R].provideSomeLayer[R](zExecutor).flatMap { rtm => ZIO.succeed(map += eventLoop -> rtm) }
      }
    } yield new Strategy[R] {
      override def runtime(ctx: Ctx): Runtime[R] = map.getOrElse(ctx.executor(), rtm)
    }
  }

}
