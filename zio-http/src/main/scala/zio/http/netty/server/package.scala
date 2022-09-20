package zio.http.netty

import zio.http._

import java.util.concurrent.atomic.AtomicReference
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object server {

  private[server] type ErrorCallbackRef = AtomicReference[Option[Server.ErrorCallback]]
  private[server] type AppRef           = AtomicReference[HttpApp[Any, Throwable]]
}
