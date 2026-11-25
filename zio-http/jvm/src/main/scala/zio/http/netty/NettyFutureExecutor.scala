/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.netty

import java.util.concurrent.CancellationException

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import io.netty.util.concurrent.{Future, GenericFutureListener}

private[zio] final class NettyFutureExecutor[A] private (jFuture: Future[A]) {

  /**
   * Waits for the future to complete. Succeeds with `Some` of the future's
   * value (`None` when the value is `null`) when the future succeeds, succeeds
   * with `None` when it was cancelled, and fails with the future's cause
   * otherwise. On interruption, removes the listener it added to the future.
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

        try jFuture.addListener(handler)
        catch {
          case failure: Throwable => cb(ZIO.fail(failure))
        }

        ()
      })
      .onInterrupt(ZIO.succeed(jFuture.removeListener(handler)))
  }

  def scoped(implicit trace: Trace): ZIO[Scope, Throwable, Option[A]] =
    execute.withFinalizer(_ => cancel(interruptIfRunning = true))

  // Cancels the future
  def cancel(interruptIfRunning: Boolean = false)(implicit trace: Trace): UIO[Boolean] =
    ZIO.succeed(jFuture.cancel(interruptIfRunning))
}

/** Turns Netty futures into ZIO effects. */
object NettyFutureExecutor {

  /**
   * Returns an executor for the future `jFuture`, which is evaluated when the
   * effect runs.
   */
  def make[A](jFuture: => Future[A])(implicit trace: Trace): Task[NettyFutureExecutor[A]] =
    ZIO.succeed(new NettyFutureExecutor(jFuture))

  /**
   * Waits for the future to complete. Succeeds when the future succeeds or is
   * cancelled, and fails with the future's cause when it fails. The wait can be
   * interrupted, and interrupting it does not cancel the future.
   */
  def executed[A](jFuture: => Future[A])(implicit trace: Trace): Task[Unit] =
    ZIO.suspend(new NettyFutureExecutor(jFuture).execute.unit)

  /**
   * Waits for the future like `executed`, except that the wait cannot be
   * interrupted: an interruption takes effect once the future completes. It
   * also adds a finalizer to the scope that cancels the future, which has no
   * effect on a future that has completed.
   */
  def scoped[A](jFuture: => Future[A])(implicit trace: Trace): ZIO[Scope, Throwable, Unit] =
    ZIO.suspend(new NettyFutureExecutor(jFuture).scoped.unit)
}
