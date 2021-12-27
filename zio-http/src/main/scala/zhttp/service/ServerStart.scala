package zhttp.service

import zhttp.service.ServerStart._
import zhttp.service.server.LeakDetectionLevel
import zhttp.service.server.LeakDetectionLevel.SIMPLE

trait ServerStart { self =>

  def apply(c: Config = Config()): Config = self match {
    case Port(n)             => c.copy(port = n)
    case Concat(self, other) => other(self(c))
    case LeakDetection(l)    => c.copy(leakDetection = l)
    case _ => c
  }

  def ++(other: ServerStart): ServerStart =
    Concat(self, other)
}

object ServerStart {
  case class Config(
    port: Int = 0,
    leakDetection: LeakDetectionLevel = SIMPLE,
  )

  private final case class Port(n: Int)                                  extends ServerStart
  private final case class LeakDetection(level: LeakDetectionLevel)      extends ServerStart
  private final case class Concat(self: ServerStart, other: ServerStart) extends ServerStart

  def port(n: Int): ServerStart = Port(n)

  def leakDetection(l: LeakDetectionLevel): ServerStart = LeakDetection(l)
}
