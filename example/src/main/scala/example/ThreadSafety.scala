package example

import zhttp.http.{HttpApp, Method, Response, _}
import zhttp.service.Server
import zio.duration._
import zio.{App, ExitCode, UIO, URIO, ZIO}

object ThreadSafety extends App {

  def printThread(tag: String) =
    UIO(println(s"${tag.padTo(6, ' ')}: ${Thread.currentThread().getName}"))

  val app = HttpApp.collectM { case Method.GET -> !! / "text" =>
    for {
      _  <- printThread("Start")
      f1 <- ZIO.sleep(1 second).zipLeft(printThread("First")).fork
      f2 <- ZIO.sleep(1 second).zipLeft(printThread("Second")).fork
      _  <- f1.join <*> f2.join
    } yield Response.text("Hello World!")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
