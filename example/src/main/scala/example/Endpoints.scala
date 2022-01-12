package example

import zhttp.endpoint._
import zhttp.http.Method.GET
import zhttp.http.Response
import zhttp.service.Server
import zio._

object Endpoints extends ZIOAppDefault {
  def h1 = GET / "a" / *[Int] / "b" / *[Boolean] to { a =>
    Response.text(a.params.toString)
  }

  def h2 = GET / "b" / *[Int] / "b" / *[Boolean] to { a =>
    Response.text(a.params.toString)
  }

  def h3 = GET / "b" / *[Int] / "c" / *[Boolean] to { a =>
    UIO(Response.text(a.params.toString))
  }

  // Run it like any simple app
  val run =
    Server.start(8091, (h3 ++ h2 ++ h1).silent).exitCode
}
