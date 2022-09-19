package zio.http

import zio._

import java.util.concurrent.atomic.AtomicReference

trait Driver {
  def start: RIO[Scope, Int]

  def setErrorCallback(newCallback: Option[Server.ErrorCallback]): Unit

  def addApp(newApp: HttpApp[Any, Throwable]): Unit
}

object Driver {

  private[zio] final case class Context private (
    httpAppRef: AtomicReference[HttpApp[Any, Throwable]],
    errorCallbackRef: AtomicReference[Option[Server.ErrorCallback]],
  ) {

    def errorCallback: Option[Server.ErrorCallback] = errorCallbackRef.get

    def onApp[A](f: HttpApp[Any, Throwable] => A): A = f(httpAppRef.get())

    def onAppRef[A](f: AtomicReference[HttpApp[Any, Throwable]] => A): A = f(httpAppRef)

    def onErrorCallbackRef[A](f: AtomicReference[Option[Server.ErrorCallback]] => A): A = f(errorCallbackRef)

  }

  object Context {

    private[zio] def empty = Context(new AtomicReference(Http.empty), new AtomicReference(Option.empty))
  }
}
