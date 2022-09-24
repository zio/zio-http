package zio.http.netty

import io.netty.util.concurrent.{Future, GenericFutureListener}
import zio._

import java.util.concurrent.CancellationException
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] final class NettyFutureExecutor[A] private (jFuture: Future[A]) {

  /**
   * Resolves when the underlying future resolves and removes the handler
   * (output: A) - if the future is resolved successfully (cause: None) - if the
   * future fails with a CancellationException (cause: Throwable) - if the
   * future fails with any other Exception
   */
  def execute(implicit trace: Trace): Task[Option[A]] = {
    var handler: GenericFutureListener[Future[A]] = { _ => {} }
    ZIO
      .async[Any, Throwable, Option[A]](cb => {
        handler = _ => {
          jFuture.cause() match {
            case null                     => cb(ZIO.attempt(Option(jFuture.get)))
            case _: CancellationException => cb(ZIO.succeed(Option.empty))
            case cause                    => cb(ZIO.fail(cause))
          }
        }
        jFuture.addListener(handler)
      })
      .onInterrupt(ZIO.succeed(jFuture.removeListener(handler)))
  }

  def scoped(implicit trace: Trace): ZIO[Scope, Throwable, Option[A]] = {
    execute.withFinalizer(_ => cancel(true))
  }

  // Cancels the future
  def cancel(interruptIfRunning: Boolean = false)(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(jFuture.cancel(interruptIfRunning))
}

object NettyFutureExecutor {
  def make[A](jFuture: => Future[A])(implicit trace: Trace): Task[NettyFutureExecutor[A]] =
    ZIO.attempt(new NettyFutureExecutor(jFuture))

  def executed[A](jFuture: => Future[A])(implicit trace: Trace): Task[Unit] = make(jFuture).flatMap(_.execute.unit)

  def scoped[A](jFuture: => Future[A])(implicit trace: Trace): ZIO[Scope, Throwable, Unit] =
    make(jFuture).flatMap(_.scoped.unit)
}
