import io.netty.handler.codec.http.{
  DefaultHttpHeaders => JDefaultHttpHeaders,
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpHeaderNames => JHttpHeaderNames,
  HttpObject => JHttpObject,
  HttpRequest => JHttpRequest,
  HttpResponseStatus => JHttpResponseStatus,
  LastHttpContent => JLastHttpContent,
}
import zhttp.channel._
import zhttp.http._
import zhttp.service.Server
import zio._

object EchoChannel extends App {
  // Echo File

  val health = Http.collect[JHttpRequest] {
    case req if req.uri() == "/health" =>
      new JDefaultHttpResponse(
        req.protocolVersion(),
        JHttpResponseStatus.OK,
        new JDefaultHttpHeaders().set(JHttpHeaderNames.CONTENT_LENGTH, "0"),
      )
  }

  val notFound = Http.collect[JHttpRequest]({ case req =>
    new JDefaultHttpResponse(
      req.protocolVersion(),
      JHttpResponseStatus.NOT_FOUND,
      new JDefaultHttpHeaders().set(JHttpHeaderNames.CONTENT_LENGTH, "0"),
    )
  })

  val eg2 = (health +++ notFound)
    .contraFlatMap[Event[JHttpObject]] {
      case Event.Read(data: JHttpRequest) => Http.succeed(data)
      case _                              => Http.empty
    }
    .map { data =>
      Operation.write(data) ++
        Operation.write(JLastHttpContent.EMPTY_LAST_CONTENT) ++
        Operation.flush
    }

  val app = HttpChannel(eg2)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server.start0(8090, app).exitCode
  }
}
