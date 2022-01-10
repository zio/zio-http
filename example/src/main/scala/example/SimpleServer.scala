package example

import zhttp.http._
import zhttp.service.Server
import zio._
object SimpleServer extends App {

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(7777, Http.ok.silent).exitCode
}
