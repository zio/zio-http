import zhttp.http._
import zhttp.service.Server
import zio._
import zio.stream.ZStream

/**
 * In this example we send Response content as, a Stream of Chunk[Byte]
 */
object StreamingResponse extends App {
  // message as Chunk[Byte]
  val message = Chunk.fromArray("Hello world !\r\n".getBytes(HTTP_CHARSET))
  val app     = Http.collect {
    case Method.GET -> Root / "health" => Response.ok
    case Method.GET -> Root / "stream" =>
      Response.http(
        status = Status.OK,
        headers = List(Header.contentLength(message.length.toLong)),
        content = HttpData.fromStream(ZStream.succeed(message)), // creates Content as Stream
      )

  }
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    /**
     * To configure the server refer example [[HelloWorldAdvanced]]
     */
    Server.start(8090, app.silent).exitCode
  }
}
