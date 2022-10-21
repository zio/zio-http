package zio.http

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait Driver {
  def start(implicit trace: Trace): RIO[Scope, Int]

  def setErrorCallback(newCallback: Option[Server.ErrorCallback])(implicit trace: Trace): UIO[Unit]

  def addApp[R](newApp: HttpApp[R, Throwable], env: ZEnvironment[R])(implicit trace: Trace): UIO[Unit]
}
