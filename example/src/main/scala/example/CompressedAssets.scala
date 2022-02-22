package example

import zhttp.http.Middleware.serveCompressed
import zhttp.http._
import zhttp.http.middleware.CompressionFormat
import zhttp.service.Server
import zio.{App, ExitCode, URIO}

object CompressedAssets extends App {
  val static = Http.collectHttp[Request] { case Method.GET -> "static" /: path =>
    for {
      file <- Http.getResourceAsFile(path.encode)
      http <- {
        if (file.isFile) Http.fromFile(file)
        else Http.notFound
      }
    } yield http
  }

  val app = (static ++ Http.notFound) @@ serveCompressed(
    Set[CompressionFormat](CompressionFormat.Brotli(), CompressionFormat.Gzip()),
  )

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
