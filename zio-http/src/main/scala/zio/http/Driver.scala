package zio.http

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait Driver {
  def start(implicit trace: Trace): RIO[Scope, Int]

  def setErrorCallback(newCallback: Option[Server.ErrorCallback])(implicit trace: Trace): UIO[Unit]

  def addApp[R](newApp: App[R], env: ZEnvironment[R])(implicit trace: Trace): UIO[Unit]

  def createClientDriver(config: ClientConfig)(implicit trace: Trace): ZIO[Scope, Throwable, ClientDriver]
}
