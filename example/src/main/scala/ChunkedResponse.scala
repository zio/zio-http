import zhttp.http._
import zhttp.service.Server
import zio._
import zio.duration._
import zio.stream.ZStream

object ChunkedResponse extends App {
  val app                                                        = Http.collect { case Method.GET -> Root / "chunked" =>
    Response.http(
      status = Status.OK,
      content = HttpData.StreamData(
        ZStream
          .repeat(Chunk.fromArray("Hello world !\r\n".getBytes(HTTP_CHARSET)))
          .schedule(Schedule.spaced(100 millisecond))
          .take(10),
      ),
    )
  }
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = Server.start(8090, app.silent).exitCode
}
