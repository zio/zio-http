package zio.http

import zio._
import zio.http.Server.ErrorCallback
import zio.http.netty.server._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

trait Server {
  def install[R](httpApp: App[R], errorCallback: Option[ErrorCallback] = None)(implicit
    trace: Trace,
  ): URIO[R, Unit]

  def port: Int

}

object Server {

  type ErrorCallback = Cause[Nothing] => ZIO[Any, Nothing, Unit]
  def serve[R](
    httpApp: App[R],
    errorCallback: Option[ErrorCallback] = None,
  )(implicit trace: Trace): URIO[R with Server, Nothing] =
    install(httpApp, errorCallback) *> ZIO.never

  def install[R](
    httpApp: App[R],
    errorCallback: Option[ErrorCallback] = None,
  )(implicit trace: Trace): URIO[R with Server, Int] = {
    ZIO.serviceWithZIO[Server](_.install(httpApp, errorCallback)) *> ZIO.service[Server].map(_.port)
  }

  def defaultWithPort(port: Int)(implicit trace: Trace): ZLayer[Any, Throwable, Server] =
    defaultWith(_.port(port))

  def defaultWith(f: ServerConfig => ServerConfig)(implicit trace: Trace): ZLayer[Any, Throwable, Server] =
    live(f(ServerConfig.default))

  val default: ZLayer[Any, Throwable, Server] = {
    implicit val trace = Trace.empty
    ServerConfig.live >>> live
  }

  def live(conf: ServerConfig)(implicit trace: Trace): ZLayer[Any, Throwable, Server] = {
    ServerConfig.live(conf) >>> Server.live
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
    override def install[R](httpApp: App[R], errorCallback: Option[ErrorCallback])(implicit
      trace: Trace,
    ): URIO[R, Unit] =
      ZIO.environment[R].flatMap(driver.addApp(httpApp, _)) *> setErrorCallback(errorCallback)

    override def port: Int = bindPort

    private def setErrorCallback(errorCallback: Option[ErrorCallback])(implicit trace: Trace): UIO[Unit] =
      driver
        .setErrorCallback(errorCallback)
        .unless(errorCallback.isEmpty)
        .map(_.getOrElse(()))

  }

}
