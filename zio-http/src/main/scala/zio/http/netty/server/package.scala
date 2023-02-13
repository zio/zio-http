package zio.http.netty

import zio._
import zio.http._

import java.util.concurrent.atomic.AtomicReference

package object server {

  private[server] type ErrorCallbackRef = AtomicReference[Option[Server.ErrorCallback]]
  private[server] type AppRef           = AtomicReference[(App[Any], ZEnvironment[Any])]
  private[server] type EnvRef           = AtomicReference[ZEnvironment[Any]]
}
