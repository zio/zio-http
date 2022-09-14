package zio.http

import zio._

trait Driver[R] {
  def start(httpApp: HttpApp[R, Throwable]): RIO[R with Scope, Unit] 
}
