package example

import zhttp.endpoint._
import zhttp.http.Method.GET
import zhttp.http.Response
import zhttp.service.Server
import zio._

object Endpoints extends ZIOAppDefault {

  def app = GET / "a" / *[Int] / "b" / *[Boolean] to { a =>
    Response.text(a.params.toString)
  }

  // Run it like any simple app
  val run =
    Server.start(8091, app)
}
