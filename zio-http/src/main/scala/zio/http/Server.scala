package zio.http

import zio._
import zio.http.Server.ErrorCallback
import zio.http.netty.server._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait Server {
  def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback] = None)(implicit
    trace: Trace,
  ): URIO[R, Unit]

  def port: Int

}

object Server {

  type ErrorCallback = Throwable => ZIO[Any, Nothing, Unit]
  def serve[R](
    httpApp: HttpApp[R, Throwable],
    errorCallback: Option[ErrorCallback] = None,
  )(implicit trace: Trace): URIO[R with Server, Nothing] =
    install(httpApp, errorCallback) *> ZIO.never

  def install[R](
    httpApp: HttpApp[R, Throwable],
    errorCallback: Option[ErrorCallback] = None,
  )(implicit trace: Trace): URIO[R with Server, Int] = {
    ZIO.serviceWithZIO[Server](_.install(httpApp, errorCallback)) *> ZIO.service[Server].map(_.port)
  }

  val default: ZLayer[Any, Throwable, Server] = {
    implicit val trace = Trace.empty
    ServerConfig.live >>> live
  }

  val live: ZLayer[ServerConfig, Throwable, Server] = {
    implicit val trace = Trace.empty
    NettyDriver.default >>> base
  }

  val base: ZLayer[Driver, Throwable, Server] = {
    implicit val trace = Trace.empty
    ZLayer.scoped {
      for {
        driver <- ZIO.service[Driver]
        port   <- driver.start
      } yield ServerLive(driver, port)
    }
  }

  private final case class ServerLive(
    driver: Driver,
    bindPort: Int,
  ) extends Server {
    override def install[R](httpApp: HttpApp[R, Throwable], errorCallback: Option[ErrorCallback])(implicit
      trace: Trace,
    ): URIO[R, Unit] =
      ZIO.environment[R].flatMap { env =>
        driver.addApp(
          if (env == ZEnvironment.empty) httpApp.asInstanceOf[HttpApp[Any, Throwable]]
          else httpApp.provideEnvironment(env),
        )

      } *> setErrorCallback(errorCallback)

    override def port: Int = bindPort

    private def setErrorCallback(errorCallback: Option[ErrorCallback])(implicit trace: Trace): UIO[Unit] =
      driver
        .setErrorCallback(errorCallback)
        .unless(errorCallback.isEmpty)
        .map(_.getOrElse(()))

  }

}
