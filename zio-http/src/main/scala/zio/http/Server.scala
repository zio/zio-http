package zio.http

import zio._
import zio.http.Server.ErrorCallback
import zio.http.netty.server._

trait Server {
  def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback] = None): URIO[R, Unit]

  def port: Int

}

object Server {

  type ErrorCallback = Throwable => ZIO[Any, Nothing, Unit]
  def serve[R](
    httpApp: HttpApp[R, Throwable],
    errorCallback: Option[ErrorCallback] = None,
  ): URIO[R with Server, Nothing] =
    install(httpApp, errorCallback) *> ZIO.never

  def install[R](
    httpApp: HttpApp[R, Throwable],
    errorCallback: Option[ErrorCallback] = None,
  ): URIO[R with Server, Int] = {
    ZIO.serviceWithZIO[Server](_.install(httpApp, errorCallback)) *> ZIO.service[Server].map(_.port)
  }

  val default = ServerConfig.live >>> live

  val live = NettyDriver.default >>> base

  val base: ZLayer[
    Driver with Driver.Context,
    Throwable,
    Server,
  ] = ZLayer.scoped {
    for {
      driver    <- ZIO.service[Driver]
      driverCtx <- ZIO.service[Driver.Context]
      port      <- driver.start()
    } yield ServerLive(driverCtx, port)
  }

  private final case class ServerLive(
    driverCtx: Driver.Context,
    bindPort: Int,
  ) extends Server {
    override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback]): URIO[R, Unit] =
      ZIO.environment[R].map { env =>
        val newApp =
          if (env == ZEnvironment.empty) httpApp.asInstanceOf[HttpApp[Any, Throwable]]
          else httpApp.provideEnvironment(env)
        var loop   = true
        while (loop) {
          driverCtx.onAppRef { appRef =>
            val oldApp = appRef.get()
            if (appRef.compareAndSet(oldApp, newApp ++ oldApp)) loop = false
          }
        }
        ()
      } *> setErrorCallback(errorCallback)

    override def port: Int = bindPort

    private def setErrorCallback(errorCallback: Option[ErrorCallback]): UIO[Unit] = {
      ZIO
        .environment[Any]
        .as {
          var loop = true
          while (loop) {
            driverCtx.onErrorCallbackRef { errorRef =>
              val oldErrorCallback = errorRef.get()
              if (errorRef.compareAndSet(oldErrorCallback, errorCallback)) loop = false
            }
          }
          ()
        }
        .unless(errorCallback.isEmpty)
        .map(_.getOrElse(()))
    }
  }

}
