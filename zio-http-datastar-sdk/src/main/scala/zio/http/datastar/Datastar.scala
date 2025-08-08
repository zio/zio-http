package zio.http.datastar

import zio._

import zio.http.ServerSentEvent

final class Datastar(private[http] val queue: Queue[ServerSentEvent[String]]) extends AnyVal

object Datastar {
  private[http] def apply(queue: Queue[ServerSentEvent[String]]): Datastar =
    new Datastar(queue)

  private[http] val done: ServerSentEvent[String] = ServerSentEvent("done")

  private[http] def make: ZIO[Scope, Nothing, Datastar] =
    ZIO.acquireRelease(Queue.unbounded[ServerSentEvent[String]].map(Datastar(_)))(_.queue.shutdown)
}
