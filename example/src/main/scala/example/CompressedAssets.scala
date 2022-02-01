package example

import zhttp.http.Middleware.serveCompressed
import zhttp.http._
import zhttp.http.middleware.CompressionFormat
import zio.{App, ExitCode, URIO}

import java.io.File

object CompressedAssets extends App {
  val static: UHttpApp = Http.collectHttp[Request] {
    case req @ Method.GET -> path if path.toString.startsWith("/app/static") && path.toString.endsWith(".gz") =>
      println(s"gzip ${req.url}")
      val filePath = new File(req.path.toList.mkString("/"))
      Http.text(filePath.getAbsoluteFile().toString())
    case req @ Method.GET -> path if path.toString.startsWith("/app/static") && path.toString.endsWith(".br") =>
      println(s"brotli ${req.url}")
      val filePath = new File(req.path.toList.mkString("/"))
      Http.text(filePath.getAbsoluteFile().toString())
    case req @ Method.GET -> path if path.toString.startsWith("/app/static") && path.toString.endsWith(".js") =>
      println(req.url)
      val filePath = new File(req.path.toList.mkString("/"))
      Http.text(filePath.getAbsoluteFile().toString())
  }
  val app: UHttpApp    =
    static @@ (serveCompressed(Set[CompressionFormat](CompressionFormat.Brotli(), CompressionFormat.Gzip())))

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val request         = Request(Method.GET, URL.fromString("/app/static/script.js").toOption.get)
    val gzipReq         = request.copy(headers = Headers(HeaderNames.acceptEncoding, HeaderValues.gzip))
    val brReq           = request.copy(headers = Headers(HeaderNames.acceptEncoding, HeaderValues.br))
    val deflateReq      = request.copy(headers = Headers(HeaderNames.acceptEncoding, HeaderValues.deflate))
    val brGzipReq       =
      request.copy(headers = Headers(HeaderNames.acceptEncoding, s"${HeaderValues.br}, ${HeaderValues.gzip}"))
    val brGzipReqWeight =
      request.copy(headers =
        Headers(HeaderNames.acceptEncoding, s"${HeaderValues.br};q=0.9, ${HeaderValues.gzip};q=0.8"),
      )

    app(request).exitCode *>
      app(gzipReq).exitCode *>
      app(brReq).exitCode *>
      app(deflateReq).exitCode *>
      app(brGzipReqWeight).exitCode *>
      app(brGzipReq).exitCode
  }
}
