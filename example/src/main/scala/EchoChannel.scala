import io.netty.buffer.{ByteBuf => JByteBuf}
import zhttp.channel._
import zhttp.http._
import zhttp.service.Server
import zio._

object EchoChannel extends App {

  val app: HttpChannel[Any, Nothing, JByteBuf, JByteBuf] =
    HttpChannel.collect[JByteBuf] {

      case Event.Request(_, _, _) =>
        Operation.response(Status.OK, Header.transferEncodingChunked :: Nil) ++
          Operation.flush ++
          Operation.read

      case Event.Content(data) =>
        Operation.content(data)

      case Event.Complete =>
        Operation.flush ++
          Operation.read

      case Event.End(data) =>
        Operation.end(data) ++
          Operation.flush
    }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server.start0(8090, app).exitCode
  }
}
