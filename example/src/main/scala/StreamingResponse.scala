import zhttp.http._
import zhttp.service.Server
import zio._
import zio.stream.ZStream

object StreamingResponse extends App {
  val message                                                    = Chunk.fromArray("Hello world !\r\n".getBytes(HTTP_CHARSET))
  val app                                                        = Http.collect {
    case Method.GET -> Root / "health" => Response.ok
    case Method.GET -> Root / "stream" =>
      Response.http(
        status = Status.OK,
        headers = List(Header.contentLength(message.length.toLong)),
        content = HttpData.fromStream(ZStream.succeed(message)),
      )

  }
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = Server.start(8090, app.silent).exitCode
}
