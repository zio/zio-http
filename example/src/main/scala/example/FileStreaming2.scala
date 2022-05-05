package example
import zhttp.http._
import zhttp.service.Server
import zio._
import zio.stream.ZSink

import java.io.{BufferedOutputStream, FileOutputStream}

object FileStreaming2 extends App {

  val app = Http.collectZIO[Request] { case req @ Method.PUT -> !! / "echo" =>
    val out = new BufferedOutputStream(new FileOutputStream("target/test"))
    for {
      byteCount <- req.bodyAsStream.run(
        ZSink.fromOutputStream(out),
      )
      _         <- UIO(out.close())

    } yield Response.text(s"$byteCount stored")
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server.start(8090, app).exitCode
  }
}
