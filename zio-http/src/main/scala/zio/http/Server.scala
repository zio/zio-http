package zio.http

import zio._
import zio.http.Server.ErrorCallback
import zio.http.netty.server._

trait Server {
  def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback] = None): URIO[R, Unit]

  def port: Int

}

object Server {
  val tag: Tag[Server] = Tag[Server]

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
    Driver,
    Throwable,
    Server,
  ] = ZLayer.scoped {
    for {
      driver <- ZIO.service[Driver]
      port   <- driver.start
    } yield ServerLive(driver, port)
  }

  private[http] val DriverHardcoded =
    new Driver {
      override def start: RIO[Scope, Int] = ZIO.succeed(8080)

      override def setErrorCallback(newCallback: Option[ErrorCallback]): UIO[Unit] = ???

      override def addApp(newApp: HttpApp[Any, Throwable]): UIO[Unit] = ???
    }

  private[http] val ServerLiveHardcoded =
    ServerLive(DriverHardcoded, 8080)

  private[http] final case class ServerLive(
    driver: Driver,
    bindPort: Int,
  ) extends Server {
    override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback]): URIO[R, Unit] =
      ZIO.environment[R].flatMap { env =>
        driver.addApp(
          if (env == ZEnvironment.empty) httpApp.asInstanceOf[HttpApp[Any, Throwable]]
          else httpApp.provideEnvironment(env),
        )

      } *> setErrorCallback(errorCallback)

    override def port: Int = bindPort

    private def setErrorCallback(errorCallback: Option[ErrorCallback]): UIO[Unit] = {
      ZIO
        .environment[Any]
        .flatMap(_ => driver.setErrorCallback(errorCallback))
        .unless(errorCallback.isEmpty)
        .map(_.getOrElse(()))
    }
  }

}
