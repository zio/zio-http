package zio.http
package netty

import zio._
import io.netty.channel._
import io.netty.util.concurrent.GenericFutureListener
import io.netty.util.concurrent.Future
import scala.jdk.CollectionConverters._

private[zio] trait NettyRuntime[R] { self =>

  private[zio] val log = NettyDriver.log

  def runtime(ctx: ChannelHandlerContext): Runtime[R]

  def unsafeRun(ctx: ChannelHandlerContext, interruptOnClose: Boolean = true)(program: ZIO[R, Throwable, Any]): Unit = {
    val rtm: Runtime[R] = runtime(ctx)

    def closeListener(rtm: Runtime[Any], fiber: Fiber.Runtime[_, _]): GenericFutureListener[Future[_ >: Void]] =
      (_: Future[_ >: Void]) =>
        Unsafe.unsafe { implicit unsafe =>
          val _ = rtm.unsafe.fork {
            fiber.interrupt.as(log.debug(s"Interrupted Fiber: [${fiber.id}]"))
          }
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
    Unsafe.unsafe { implicit unsafe =>
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
  }

  def unsafeRunUninterruptible(ctx: ChannelHandlerContext)(program: ZIO[R, Throwable, Any]): Unit =
    unsafeRun(ctx, interruptOnClose = false)(program)
}

object NettyRuntime {

  /**
   * Creates a runtime that uses a separate thread pool for ZIO operations.
   */
  def usingDedicatedThreadPool[R: Tag] = ZLayer.fromZIO {
    ZIO
      .runtime[R]
      .map(rtm =>
        new NettyRuntime[R] {
          def runtime(ctx: ChannelHandlerContext): Runtime[R] = rtm
        },
      )
  }

  /**
   * Creates a runtime that uses the same thread that's used by the channel's
   * event loop. This should be the preferred way of creating the runtime for
   * the server.
   */
  def usingSharedThreadPool[R: Tag] =
    ZLayer.fromZIO {
      for {
        elg      <- ZIO.service[EventLoopGroup]
        provider <- ZIO.runtime[R].flatMap { runtime =>
          ZIO
            .foreach(elg.asScala) { javaExecutor =>
              val executor = Executor.fromJavaExecutor(javaExecutor)
              ZIO.runtime[R].onExecutor(executor).map { runtime =>
                javaExecutor -> runtime
              }
            }
            .map { iterable =>
              val map = iterable.toMap
              (ctx: ChannelHandlerContext) => map.getOrElse(ctx.executor(), runtime)
            }
        }
      } yield new NettyRuntime[R] {
        def runtime(ctx: ChannelHandlerContext): Runtime[R] = provider(ctx)
      }
    }

  // trait RuntimeProvider[R] {
  //   def runtime(ctx: ChannelHandlerContext): Runtime[R]
  // }

  // object RuntimeProvider {

  //   /**
  //    * Creates a runtime that uses a separate thread pool for ZIO operations.
  //    */
  //   def default[R]: URIO[R, RuntimeProvider[R]] = ZIO.runtime[R].map(rtm => (ctx: ChannelHandlerContext) => rtm)

  //   /**
  //    * z Creates a runtime that uses the same thread that's used by the
  //    * channel's event loop. This should be the preferred way of creating the
  //    * runtime for the server.
  //    */
  //   def sticky[R]: URIO[R with EventLoopGroup, RuntimeProvider[R]] =
  //     for {
  //       elg      <- ZIO.service[EventLoopGroup]
  //       provider <- ZIO.runtime[R].flatMap { runtime =>
  //         ZIO
  //           .foreach(elg.asScala) { javaExecutor =>
  //             val executor = Executor.fromJavaExecutor(javaExecutor)
  //             ZIO.runtime[R].onExecutor(executor).map { runtime =>
  //               javaExecutor -> runtime
  //             }
  //           }
  //           .map { iterable =>
  //             val map = iterable.toMap
  //             (ctx: ChannelHandlerContext) => map.getOrElse(ctx.executor(), runtime)
  //           }
  //       }
  //     } yield new RuntimeProvider[R] {
  //       def runtime(ctx: ChannelHandlerContext): Runtime[R] = provider(ctx)
  //     }

  // }
}
