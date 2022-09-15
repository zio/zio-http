package zio.http

import zio._
// import java.util.concurrent.atomic.AtomicReference

trait Driver {
  def start(): RIO[Scope, Int]
}
