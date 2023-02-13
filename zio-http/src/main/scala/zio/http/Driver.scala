package zio.http

import zio._

trait Driver {
  def start: RIO[Scope, Int]

  def setErrorCallback(newCallback: Option[Server.ErrorCallback]): UIO[Unit]

  def addApp[R](newApp: App[R], env: ZEnvironment[R]): UIO[Unit]

  def createClientDriver(config: ClientConfig): ZIO[Scope, Throwable, ClientDriver]
}
