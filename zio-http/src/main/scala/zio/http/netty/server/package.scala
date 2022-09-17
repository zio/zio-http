package zio.http.netty

import java.util.concurrent.atomic.AtomicReference
import zio.http._

package object server {
  
  private[server] type ErrorCallbackRef = AtomicReference[Option[Server.ErrorCallback]]
  private[server] type AppRef  = AtomicReference[HttpApp[Any, Throwable]]
}
