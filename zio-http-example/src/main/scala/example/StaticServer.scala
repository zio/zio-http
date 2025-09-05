//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio.ZIOAppDefault

import zio.http._
import zio.http.codec.PathCodec.trailing
import zio.http.template._

object StaticServer extends ZIOAppDefault {

  // A simple app to serve static resource files from a local directory.
  val app = Routes(
    Method.GET / "static" / trailing -> handler {
      val extractPath    = Handler.param[(Path, Request)](_._1)
      val extractRequest = Handler.param[(Path, Request)](_._2)

      for {
        path <- extractPath
        file <- Handler.getResourceAsFile(path.encode)
        http <-
        // Rendering a custom UI to list all the files in the directory
        extractRequest >>> (if (file.isDirectory) {
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
                                        href := s"$base${path.encode}${if (path.isRoot) file.getName
                                          else "/" + file.getName}",
                                        file.getName,
                                      ),
                                    )
                                  },
                                )
                              }
                            }

                            // Return the file if it's a static resource
                            else if (file.isFile) Handler.fromFile(file)

                            // Return a 404 if the file doesn't exist
                            else Handler.notFound)
      } yield http
    },
  ).sandbox

  val run = Server.serve(app).provide(Server.default)

}
