package example

import zhttp.endpoint._
import zhttp.http.Method.GET
import zhttp.http.Response
import zhttp.service.Server
import zio.{App, ExitCode, URIO}

object Endpoints extends App {
  def app = GET / "a" / *[Int] / "b" / *[Boolean] to { a =>
    Response.text(a.params.toString)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8091, app).exitCode
}
