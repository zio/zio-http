package zhttp.service

import io.netty.util.concurrent.{Future, GenericFutureListener}
import zio._

import java.util.concurrent.CancellationException

final class ChannelFuture[A] private (jFuture: Future[A]) {

  /**
   * Resolves when the underlying future resolves and removes the handler (output: A) - if the future is resolved
   * successfully (cause: None) - if the future fails with a CancellationException (cause: Throwable) - if the future
   * fails with any other Exception
   */
  def execute: Task[Option[A]] = {
    var handler: GenericFutureListener[Future[A]] = { _ => {} }
    ZIO
      .effectAsync[Any, Throwable, Option[A]](cb => {
        handler = _ => {
          jFuture.cause() match {
            case null                     => cb(Task(Option(jFuture.get)))
            case _: CancellationException => cb(UIO(Option.empty))
            case cause                    => cb(ZIO.fail(cause))
          }
        }
        jFuture.addListener(handler)
      })
      .onInterrupt(UIO(jFuture.removeListener(handler)))
  }

  def toManaged: ZManaged[Any, Throwable, Option[A]] = {
    execute.toManaged(_ => cancel(true))
  }

  // Cancels the future
  def cancel(interruptIfRunning: Boolean = false): UIO[Boolean] = UIO(jFuture.cancel(interruptIfRunning))
}

object ChannelFuture {
  def make[A](jFuture: => Future[A]): Task[ChannelFuture[A]] = Task(new ChannelFuture(jFuture))

  def unit[A](jFuture: => Future[A]): Task[Unit] = make(jFuture).flatMap(_.execute.unit)

  def asManaged[A](jFuture: => Future[A]): TaskManaged[Unit] = make(jFuture).toManaged_.flatMap(_.toManaged.unit)
}
