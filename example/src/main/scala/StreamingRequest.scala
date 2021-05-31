import zhttp.http._
import zhttp.service.Server
import zio._

/**
 * Example to read request content as a Stream
 */
object StreamingRequest extends App {

  val app = Http.collect[Request] {
    case _ -> Root / "health" => Response.ok
    case _ -> Root / "upload" =>
      Response.decodeBuffered(8) { q =>
        Response(
          status = Status.OK,
          content = HttpData.fromQueueIn(q),
          headers = Header.transferEncodingChunked :: Nil,
        )
      }
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    // Starting the server (for more advanced startup configuration checkout `HelloWorldAdvanced`)
    Server.start(8090, app.silent).exitCode
  }
}
