package zio.http

import zio._

trait Driver {
  def start: RIO[Scope, Int]

  def setErrorCallback(newCallback: Option[Server.ErrorCallback]): UIO[Unit]

  def addApp(newApp: HttpApp[Any, Throwable]): UIO[Unit]
}
