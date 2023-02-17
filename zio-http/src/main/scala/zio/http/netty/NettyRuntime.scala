package zio.http.netty

import io.netty.channel._
import io.netty.util.concurrent.{Future, GenericFutureListener}
import zio._
import zio.http.service.Log // scalafix:ok;

private[zio] trait NettyRuntime { self =>

  private val log = Log.withTags("NettyRuntime")

  def runtime(ctx: ChannelHandlerContext): Runtime[Any]

  def run(ctx: ChannelHandlerContext, ensured: () => Unit, interruptOnClose: Boolean = true)(
    program: ZIO[Any, Throwable, Any],
  )(implicit unsafe: Unsafe, trace: Trace): Unit = {
    val rtm: Runtime[Any] = runtime(ctx)

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

    rtm.unsafe.runOrFork(program) match {
      case Left(fiber) =>
        log.debug(s"Started Fiber: [${fiber.id}]")
        if (interruptOnClose) {
          close = closeListener(rtm, fiber)
          ctx.channel().closeFuture.addListener(close)
        }
        fiber.unsafe.addObserver {
          case Exit.Success(_)     =>
            log.debug(s"Completed Fiber: [${fiber.id}]")
            removeListener(close)
            ensured()
          case Exit.Failure(cause) =>
            onFailure(cause, ctx)
            removeListener(close)
            ensured()
        }
      case Right(exit) =>
        exit match {
          case Exit.Success(_)     =>
          case Exit.Failure(cause) =>
            onFailure(cause, ctx)
        }
    }
  }

  def runUninterruptible(ctx: ChannelHandlerContext, ensured: () => Unit)(
    program: ZIO[Any, Throwable, Any],
  )(implicit unsafe: Unsafe, trace: Trace): Unit =
    run(ctx, ensured, interruptOnClose = false)(program)

  private def closeListener(rtm: Runtime[Any], fiber: Fiber.Runtime[_, _]): GenericFutureListener[Future[_ >: Void]] =
    (_: Future[_ >: Void]) => {
      val _ = rtm.unsafe.fork {
        fiber.interrupt.as(log.debug(s"Interrupted Fiber: [${fiber.id}]"))
      }(implicitly[Trace], Unsafe.unsafe)
    }
}

private[zio] object NettyRuntime {

  val noopEnsuring = () => ()

  /**
   * Runs ZIO programs from Netty handlers on the current ZIO runtime
   */
  val default: ZLayer[Any, Nothing, NettyRuntime] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.fromZIO {
      ZIO
        .runtime[Any]
        .map { rtm =>
          new NettyRuntime {
            def runtime(ctx: ChannelHandlerContext): Runtime[Any] = rtm
          }
        }
    }
  }
}
