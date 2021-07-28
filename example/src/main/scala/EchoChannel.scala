import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.{
  DefaultFullHttpResponse => JDefaultFullHttpResponse,
  DefaultHttpHeaders => JDefaultHttpHeaders,
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpContent => JHttpContent,
  HttpHeaderNames => JHttpHeaderNames,
  HttpHeaderValues => JHttpHeaderValues,
  HttpMethod => JHttpMethod,
  HttpObject => JHttpObject,
  HttpRequest => JHttpRequest,
  HttpResponseStatus => JHttpResponseStatus,
  LastHttpContent => JLastHttpContent,
}
import zhttp.experiment._
import zhttp.http._
import zhttp.service.Server
import zio._

object EchoChannel extends App {
  def chunkedHeaders = new JDefaultHttpHeaders().set(JHttpHeaderNames.TRANSFER_ENCODING, JHttpHeaderValues.CHUNKED)

  val health: Http[Any, Throwable, JHttpRequest, HttpChannel[Any, Nothing, JHttpContent, JHttpObject]] =
    Http.collect[JHttpRequest] {
      case req if req.uri() == "/health" =>
        HttpChannel.make[JHttpContent, JHttpObject] { case (Event.Register, context) =>
          ZIO.fail(new Error("What up?"))
          context.write(new JDefaultHttpResponse(req.protocolVersion(), JHttpResponseStatus.OK)) *>
            context.write(JLastHttpContent.EMPTY_LAST_CONTENT) *>
            context.flush
        }
    }

  val serverError: Http[Any, Throwable, JHttpRequest, HttpChannel[Any, Nothing, JHttpContent, JHttpObject]] =
    Http.collectM[JHttpRequest] {
      case req if req.uri() == "/server-error" => ZIO.fail(new Error("What up?"))
    }

  val upload = Http.collect[JHttpRequest] {
    case req if req.uri() == "/upload" && req.method == JHttpMethod.POST =>
      HttpChannel.make[JHttpContent, JHttpObject] {
        case (Event.Read(data: JLastHttpContent), context) => context.write(data) *> context.flush
        case (Event.Read(data: JHttpContent), context)     => context.write(data) *> context.flush *> context.read
        case (Event.Register, context)                     =>
          context.write(
            new JDefaultFullHttpResponse(req.protocolVersion(), JHttpResponseStatus.CONTINUE, JUnpooled.EMPTY_BUFFER),
          )
          context.flush *>
            context.write(
              new JDefaultHttpResponse(req.protocolVersion(), JHttpResponseStatus.OK, chunkedHeaders),
            ) *> context.flush *>
            context.read
      }
  }

//  val fullRequest = Http.collect[JHttpRequest] {}

//  case class State[A](content: A, done: Boolean = false)
//
//  val reverseText = Http.collect[JHttpRequest] {
//    case req if req.uri() == "/reverse" && req.method == JHttpMethod.POST =>
//      val contentLength = req.headers().get(JHttpHeaderNames.CONTENT_LENGTH)
//      val headers       = new JDefaultHttpHeaders().set(JHttpHeaderNames.CONTENT_LENGTH, contentLength)
//
//      HttpChannel.collectWith[JHttpContent](State[String]("")) {
//        case (State(_, false), Event.Complete) => Operation.read
//
//        case (s, Event.Read(data)) =>
//          val done   = data.isInstanceOf[JLastHttpContent]
//          val string = data.content().toString(StandardCharsets.UTF_8)
//
//          Operation.save(State(s.content ++ string, done))
//
//        case (State(content, true), Event.Complete) =>
//          val buffer   = JUnpooled.copiedBuffer(content.reverse, StandardCharsets.UTF_8)
//          val response =
//            new JDefaultFullHttpResponse(
//              req.protocolVersion(),
//              JHttpResponseStatus.OK,
//              buffer,
//              headers,
//              JEmptyHttpHeaders.INSTANCE,
//            )
//
//          Operation.write(response) ++ Operation.flush
//      }
//  }

  val notFound = Http.collect[JHttpRequest] { case req =>
    HttpChannel.make[JHttpContent, JHttpObject] { case (_, context) =>
      context.write(new JDefaultHttpResponse(req.protocolVersion(), JHttpResponseStatus.NOT_FOUND)) *>
        context.write(JLastHttpContent.EMPTY_LAST_CONTENT) *>
        context.flush
    }

  }

  val app = health +++ upload +++ serverError

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server.start0(8090, app).exitCode
  }
}
