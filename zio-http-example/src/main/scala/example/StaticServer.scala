package example

import zio.ZIOAppDefault

import zio.http._
import zio.http.codec.PathCodec.trailing
import zio.http.html._

object StaticServer extends ZIOAppDefault {

  // A simple app to serve static resource files from a local directory.
  val app = Routes(
    Method.GET / "static" / trailing -> { (path: Path) =>
      handler {
        for {
          file <- Handler.getResourceAsFile(path.encode)
          http <-
            // Rendering a custom UI to list all the files in the directory
            if (file.isDirectory) {
              // Accessing the files in the directory
              val files = file.listFiles.toList.sortBy(_.getName)
              val base  = "/static/"
              val rest  = path

              // Custom UI to list all the files in the directory
              Handler.template(s"File Explorer ~$base${path}") {
                ul(
                  li(a(href := s"$base$rest", "..")),
                  files.map { file =>
                    li(
                      a(
                        href := s"$base${path.encode}${if (path.isRoot) file.getName else "/" + file.getName}",
                        file.getName,
                      ),
                    )
                  },
                )
              }
            }

            // Return the file if it's a static resource
            else if (file.isFile) Http.fromFile(file).toHandler(Handler.notFound)

            // Return a 404 if the file doesn't exist
            else Handler.notFound
        } yield http
      }
    },
  ).ignoreErrors.toApp

  val run = Server.serve(app).provide(Server.default)

}
