package react_example

import zio._
import zio.http._
import zio.stream.ZStream

import java.io.File
import java.nio.file.Paths

object ReactHelloWorld extends ZIOAppDefault {
    // Create the build relative directory path
    private val buildDirectory = "src/main/scala/react_example/react/build"

    val app: HttpApp[Any] = Routes.singleton {
        handler { (_: Path, req: Request) => {
            if (req.url.path.toString == "/api/hello") {
                Response.text("Hello World")
            }
            if (req.url.path.toString == "/") {
                val file = new File(s"$buildDirectory/index.html")
                val length = Headers(Header.ContentLength(file.length()))
                Response(headers = length, body = Body.fromFile(file))
            }
            else {
                val tmp = req.url.path.toString
                val file = new File(s"$buildDirectory/$tmp")
                val length = Headers(Header.ContentLength(file.length()))
                Response(headers = length, body = Body.fromFile(file))
            }
          }
        }
      }.toHttpApp
    
    // Run it like any simple app
    val run = Server.serve(app).provide(Server.default) 
}
