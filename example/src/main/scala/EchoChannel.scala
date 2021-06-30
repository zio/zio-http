import io.netty.handler.codec.http.{
  DefaultHttpHeaders => JDefaultHttpHeaders,
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpContent => JHttpContent,
  HttpHeaderNames => JHttpHeaderNames,
  HttpHeaderValues => JHttpHeaderValues,
  HttpObject => JHttpObject,
  HttpRequest => JHttpRequest,
  HttpResponseStatus => JHttpResponseStatus,
  HttpVersion => JHttpVersion,
}
import zhttp.channel._
import zhttp.service.Server
import zio._

object EchoChannel extends App {
  // Echo File

  val eg =
    HttpChannel.collect[JHttpObject] {
      case Event.Read(_: JHttpRequest) =>
        Operation.write(
          new JDefaultHttpResponse(
            JHttpVersion.HTTP_1_1,
            JHttpResponseStatus.OK,
            new JDefaultHttpHeaders().set(JHttpHeaderNames.TRANSFER_ENCODING, JHttpHeaderValues.CHUNKED),
          ),
        )

      case Event.Read(data: JHttpContent) =>
        Operation.write(data)

      case Event.Complete =>
        Operation.flush ++ Operation.read
    }

  val app = eg

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server.start0(8090, app).exitCode
  }
}
