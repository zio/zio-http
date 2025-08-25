package zio.http.datastar

import zio._

import zio.http.ServerSentEvent

final case class Datastar(queue: Queue[ServerSentEvent[String]]) extends AnyVal

object Datastar {
  private[http] def make = Queue.unbounded[ServerSentEvent[String]].map(Datastar(_))
  private[http] val done = ServerSentEvent("done")
}
