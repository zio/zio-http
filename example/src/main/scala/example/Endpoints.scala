package example

import zhttp.endpoint._
import zhttp.http.Method.GET
import zhttp.http.Response
import zhttp.service.Server
import zio.{App, ExitCode, URIO}

object Endpoints extends App {
  def h1 = GET / "a" / *[Int] / "b" / *[Boolean] to { a =>
    Response.text(a.params.toString)
  }

  def h2 = GET / "b" / *[Int] / "b" / *[Boolean] to { a =>
    Response.text(a.params.toString)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8091, h2 ++ h1).exitCode
}
