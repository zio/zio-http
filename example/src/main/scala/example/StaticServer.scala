package example

import zhttp.http.Http
import zhttp.service.Server
import zio.{ExitCode, URIO}

import java.nio.file.Paths

object StaticServer extends zio.App {

  // A simple app to serve static resource files from a local directory.
  val app = Http.fromPath(Paths.get("src/main/resources/TestStatic"))

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode

  // The following requests to work
  // curl -i "http://localhost:8090/Dummy.txt"
}
