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

  def unsafeRun(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {

    val rtm = strategy.runtime(ctx)

    // Close the connection if the program fails
    // When connection closes, interrupt the program

    Unsafe.unsafeCompat{ implicit u => 
      val fiber = rtm.unsafe.fork( for {
        f <- program.fork
        _ <- ZIO.succeed {
              val listener = closeListener(rtm, f)
              ctx.channel().closeFuture.addListener(listener)
              listener
            }
      } yield ())
  
      fiber.unsafe.addObserver {
        case Exit.Success(_)     => ()
        case Exit.Failure(cause) =>
          cause.failureOption.orElse(cause.dieOption) match {
            case None    => ()
            case Some(_) => java.lang.System.err.println(cause.prettyPrint)
          }
          if (ctx.channel().isOpen) ctx.close()
          ()
        }
      }
  }
      
  def unsafeRunUninterruptible(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit = {
    val rtm = strategy.runtime(ctx)

    Unsafe.unsafeCompat{ implicit u => 
      rtm.unsafe.fork(program).unsafe.addObserver { 
        case Exit.Success(_)     => ()
        case Exit.Failure(cause) =>
          cause.failureOption.orElse(cause.dieOption) match {
            case None    => ()
            case Some(_) => java.lang.System.err.println(cause.prettyPrint)
          }
          if (ctx.channel().isOpen) ctx.close()
          ()
      }
    }
  }

  private def closeListener(rtm: Runtime[Any], fiber: Fiber.Runtime[_, _]): GenericFutureListener[Future[_ >: Void]] =
    (_: Future[_ >: Void]) => 
      Unsafe.unsafeCompat { implicit u => 
        rtm.unsafe.fork(fiber.interrupt)
        ()
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
      ZIO.executorWith { executor =>
        val dedicatedExecutor = Executor.fromExecutionContext {
          JExecutionContext.fromExecutor(group)
        }
        ZIO.onExecutor(dedicatedExecutor) {
          ZIO.runtime[R].map(runtime => Dedicated(runtime))
        }
      }

    def default[R](): ZIO[R, Nothing, Strategy[R]] =
      ZIO.runtime[R].map(runtime => Default(runtime))

    def sticky[R](group: JEventLoopGroup): ZIO[R, Nothing, Strategy[R]] =
      ZIO.runtime[R].flatMap { default =>
        ZIO
          .foreach(group.asScala) { eventLoop =>
            val stickyExecutor = Executor.fromExecutionContext {
              JExecutionContext.fromExecutor(eventLoop)
            }
            ZIO.onExecutor(stickyExecutor) {
              ZIO.runtime[R].map(runtime => eventLoop -> runtime)
            }
          }
          .map(group => Group(default, group.toMap))
      }

    case class Default[R](default: Runtime[R]) extends Strategy[R] {
      override def runtime(ctx: ChannelHandlerContext): Runtime[R] = default
    }

    case class Dedicated[R](dedicated: Runtime[R]) extends Strategy[R] {
      override def runtime(ctx: ChannelHandlerContext): Runtime[R] = dedicated
    }

    case class Group[R](default: Runtime[R], group: Map[EventExecutor, Runtime[R]]) extends Strategy[R] {
      override def runtime(ctx: ChannelHandlerContext): Runtime[R] =
        group.getOrElse(ctx.executor(), default)
    }
  }
}
