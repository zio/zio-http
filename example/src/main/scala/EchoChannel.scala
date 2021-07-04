import io.netty.handler.codec.http.{
  DefaultHttpHeaders => JDefaultHttpHeaders,
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpHeaderNames => JHttpHeaderNames,
  HttpHeaderValues => JHttpHeaderValues,
  HttpMethod => JHttpMethod,
  HttpObject => JHttpObject,
  // HttpContent => JHttpContent,
  HttpRequest => JHttpRequest,
  HttpResponseStatus => JHttpResponseStatus,
  LastHttpContent => JLastHttpContent,
}
import zhttp.channel._
import zhttp.http._
import zhttp.service.Server
import zio._

object EchoChannel extends App {
  def chunkedHeaders = new JDefaultHttpHeaders().set(JHttpHeaderNames.TRANSFER_ENCODING, JHttpHeaderValues.CHUNKED)

  val health = Http.collect[JHttpRequest] {
    case req if req.uri() == "/health" =>
      HttpChannel.write(new JDefaultHttpResponse(req.protocolVersion(), JHttpResponseStatus.OK)) ++
        HttpChannel.write(JLastHttpContent.EMPTY_LAST_CONTENT) ++
        HttpChannel.flush
  }

  val upload = Http.collect[JHttpRequest] {
    case req if req.uri() == "/upload" && req.method == JHttpMethod.POST =>
      HttpChannel.write(new JDefaultHttpResponse(req.protocolVersion(), JHttpResponseStatus.OK, chunkedHeaders)) ++
        HttpChannel.echoBody[JHttpObject]
  }

  val notFound = Http.collect[JHttpRequest] { case req =>
    HttpChannel.write(new JDefaultHttpResponse(req.protocolVersion(), JHttpResponseStatus.NOT_FOUND)) ++
      HttpChannel.write(JLastHttpContent.EMPTY_LAST_CONTENT) ++
      HttpChannel.flush
  }

  val app = health +++ upload +++ notFound

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server.start0(8090, app).exitCode
  }
}
