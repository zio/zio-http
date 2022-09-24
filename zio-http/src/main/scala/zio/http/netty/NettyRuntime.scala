package zio.http.netty

import io.netty.channel._
import io.netty.util.concurrent.{Future, GenericFutureListener}
import zio._
import zio.http.service.Log

import scala.jdk.CollectionConverters._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] trait NettyRuntime { self =>

  private val log = Log.withTags("NettyRuntime")

  def runtime(ctx: ChannelHandlerContext): Runtime[Any]

  def run(ctx: ChannelHandlerContext, interruptOnClose: Boolean = true)(
    program: ZIO[Any, Throwable, Any],
  )(implicit unsafe: Unsafe, trace: Trace): Unit = {
    val rtm: Runtime[Any] = runtime(ctx)

    def closeListener(rtm: Runtime[Any], fiber: Fiber.Runtime[_, _]): GenericFutureListener[Future[_ >: Void]] =
      (_: Future[_ >: Void]) => {
        val _ = rtm.unsafe.fork {
          fiber.interrupt.as(log.debug(s"Interrupted Fiber: [${fiber.id}]"))
        }(implicitly[Trace], Unsafe.unsafe)
      }

    def onFailure(cause: Cause[Throwable], ctx: ChannelHandlerContext): Unit = {
      cause.failureOption.orElse(cause.dieOption) match {
        case None        => ()
        case Some(error) =>
          log.error("HttpRuntimeException:" + cause.prettyPrint)
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

    val fiber = rtm.unsafe.fork(program)

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
        onFailure(cause, ctx)
        removeListener(close)
    }
  }

  def runUninterruptible(ctx: ChannelHandlerContext)(
    program: ZIO[Any, Throwable, Any],
  )(implicit unsafe: Unsafe, trace: Trace): Unit =
    run(ctx, interruptOnClose = false)(program)
}

object NettyRuntime {

  /**
   * Creates a runtime that uses a separate thread pool for ZIO operations.
   */
  val usingDedicatedThreadPool = {
    implicit val trace: Trace = Trace.empty
    ZLayer.fromZIO {
      ZIO
        .runtime[Any]
        .map(rtm =>
          new NettyRuntime {
            def runtime(ctx: ChannelHandlerContext): Runtime[Any] = rtm
          },
        )
    }
  }

  /**
   * Creates a runtime that uses the same thread that's used by the channel's
   * event loop. This should be the preferred way of creating the runtime for
   * the server.
   */
  val usingSharedThreadPool = {
    implicit val trace: Trace = Trace.empty
    ZLayer.fromZIO {
      for {
        elg      <- ZIO.service[EventLoopGroup]
        provider <- ZIO.runtime[Any].flatMap { runtime =>
          ZIO
            .foreach(elg.asScala) { javaExecutor =>
              val executor = Executor.fromJavaExecutor(javaExecutor)
              ZIO.runtime[Any].onExecutor(executor).map { runtime =>
                javaExecutor -> runtime
              }
            }
            .map { iterable =>
              val map = iterable.toMap
              (ctx: ChannelHandlerContext) => map.getOrElse(ctx.executor(), runtime)
            }
        }
      } yield new NettyRuntime {
        def runtime(ctx: ChannelHandlerContext): Runtime[Any] = provider(ctx)
      }
    }
  }

}
