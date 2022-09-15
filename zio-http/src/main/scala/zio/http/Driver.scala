package zio.http

import zio._

trait Driver {
  def start(): RIO[Scope, Int]
}
