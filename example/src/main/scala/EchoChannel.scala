import io.netty.handler.codec.http.{
  DefaultFullHttpResponse => JDefaultFullHttpResponse,
  DefaultHttpHeaders => JDefaultHttpHeaders,
  DefaultHttpResponse => JDefaultHttpResponse,
  EmptyHttpHeaders => JEmptyHttpHeaders,
  HttpContent => JHttpContent,
  HttpHeaderNames => JHttpHeaderNames,
  HttpHeaderValues => JHttpHeaderValues,
  HttpMethod => JHttpMethod,
  HttpObject => JHttpObject,
  HttpRequest => JHttpRequest,
  HttpResponseStatus => JHttpResponseStatus,
  LastHttpContent => JLastHttpContent,
}
import io.netty.buffer.{Unpooled => JUnpooled}
import zhttp.channel._
import zhttp.http._
import zhttp.service.Server
import zio._

import java.nio.charset.StandardCharsets

object EchoChannel extends App {
  def chunkedHeaders = new JDefaultHttpHeaders().set(JHttpHeaderNames.TRANSFER_ENCODING, JHttpHeaderValues.CHUNKED)

  val health = Http.collect[JHttpRequest] {
    case req if req.uri() == "/health" =>
      HttpChannel.collect[JHttpObject] {
        case Event.Complete => Operation.flush
        case Event.Read(_)  =>
          Operation.write(new JDefaultHttpResponse(req.protocolVersion(), JHttpResponseStatus.OK)) ++
            Operation.write(JLastHttpContent.EMPTY_LAST_CONTENT)
      }

  }

  val upload = Http.collect[JHttpRequest] {
    case req if req.uri() == "/upload" && req.method == JHttpMethod.POST =>
      HttpChannel.collect[JHttpObject] {
        case Event.Read(data) => Operation.write(data)
        case Event.Complete   => Operation.flush ++ Operation.read
        case Event.Register   =>
          Operation.write(new JDefaultHttpResponse(req.protocolVersion(), JHttpResponseStatus.OK, chunkedHeaders))
      }
  }

  case class State[A](content: A, done: Boolean = false)

  val reverseText = Http.collect[JHttpRequest] {
    case req if req.uri() == "/reverse" && req.method == JHttpMethod.POST =>
      val contentLength = req.headers().get(JHttpHeaderNames.CONTENT_LENGTH)
      val headers       = new JDefaultHttpHeaders().set(JHttpHeaderNames.CONTENT_LENGTH, contentLength)

      HttpChannel.collectWith[JHttpContent](State[String]("")) {
        case (State(_, false), Event.Complete) => Operation.read

        case (s, Event.Read(data)) =>
          val done   = data.isInstanceOf[JLastHttpContent]
          val string = data.content().toString(StandardCharsets.UTF_8)

          Operation.save(State(s.content ++ string, done))

        case (State(content, true), Event.Complete) =>
          val buffer   = JUnpooled.copiedBuffer(content.reverse, StandardCharsets.UTF_8)
          val response =
            new JDefaultFullHttpResponse(
              req.protocolVersion(),
              JHttpResponseStatus.OK,
              buffer,
              headers,
              JEmptyHttpHeaders.INSTANCE,
            )

          Operation.write(response) ++ Operation.flush
      }
  }

  val notFound = Http.collect[JHttpRequest] { case req =>
    HttpChannel.collect[JHttpObject] { case Event.Complete =>
      Operation.write(new JDefaultHttpResponse(req.protocolVersion(), JHttpResponseStatus.NOT_FOUND)) ++
        Operation.write(JLastHttpContent.EMPTY_LAST_CONTENT) ++
        Operation.flush
    }
  }

  val app = health +++ upload +++ reverseText +++ notFound

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server.start0(8090, app).exitCode
  }
}
