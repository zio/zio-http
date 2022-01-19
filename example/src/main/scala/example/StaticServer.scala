package example

import zhttp.http.Http
import zhttp.service.Server
import zio.{ExitCode, URIO}

import java.nio.file.Paths

object StaticServer extends zio.App {

  // Create a static file server from root path
  val app = Http.serveFilesFrom(Paths.get("src/main/resources/TestStatic"))

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode

  // The following requests to work
  // curl -i "http://localhost:8090/Dummy.txt"
}
