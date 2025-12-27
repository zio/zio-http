package example

import zio._
import zio.http._
import java.io.File

object FileRangeExample extends ZIOAppDefault {
  
  val routes = Routes(
    Method.GET / "download" / string("filename") -> handler { (filename: String, req: Request) =>
      val file = new File(s"./files/$filename")
      FileRangeSupport.serveFile(file, req, None)
    },
    
    Method.GET / Root -> handler {
      Response.text(
        """HTTP Range Request Example
          |
          |Test: curl -v -H "Range: bytes=0-10" http://localhost:8080/download/test.txt
          |""".stripMargin
      )
    }
  )
  
  def run = {
    Console.printLine("Server started at http://localhost:8080") *>
    Server.serve(routes).provide(Server.default)
  }
}
