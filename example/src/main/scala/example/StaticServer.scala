package example

import zhttp.http.{Http, Request}
import zhttp.service.Server
import zio.{ExitCode, URIO}

object StaticServer extends zio.App {

  // A simple app to serve static resource files from a local directory.
  val app = Http.collectHttp[Request] { case req => Http.fromResource(req.url.encode) }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode

  // The following requests to work
  // curl -i "http://localhost:8090/Dummy.txt"
}
