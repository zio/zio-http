package example

import zhttp.html._
import zhttp.http._
import zhttp.service.Server
import zio.{ExitCode, URIO, ZIOAppDefault}

object StaticServer extends ZIOAppDefault {

  // A simple app to serve static resource files from a local directory.
  val app = Http.collectHttp[Request] { case Method.GET -> "static" /: path =>
    for {
      file <- Http.getResourceAsFile(path.encode)
      http <-
        // Rendering a custom UI to list all the files in the directory
        if (file.isDirectory) {

          // Accessing the files in the directory
          val files = file.listFiles.toList.sortBy(_.getName)
          val base  = "/static"
          val rest  = path.dropLast(1)

          // Custom UI to list all the files in the directory
          Http.template(s"File Explorer ~$base${path}") {
            ul(
              li(a(href := s"$base$rest", "..")),
              files.map { file =>
                li(
                  a(
                    href := s"$base${path.encode}${if (path.isEnd) file.getName else "/" + file.getName}",
                    file.getName,
                  ),
                )
              },
            )
          }
        }

        // Return the file if it's a static resource
        else if (file.isFile) Http.fromFile(file)

        // Return a 404 if the file doesn't exist
        else Http.empty
    } yield http
  }

  val run: URIO[zio.ZEnv, ExitCode] = Server.start(8090, app).exitCode

}
