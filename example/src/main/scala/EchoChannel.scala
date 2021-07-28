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
  HttpVersion => JHttpVersion,
  LastHttpContent => JLastHttpContent,
}
import zhttp.experiment._
import zhttp.http._
import zhttp.service.Server
import zio._

object EchoChannel extends App {
  val chunkedHeaders = new JDefaultHttpHeaders().set(JHttpHeaderNames.TRANSFER_ENCODING, JHttpHeaderValues.CHUNKED)
  val res100         = new JDefaultFullHttpResponse(JHttpVersion.HTTP_1_1, JHttpResponseStatus.CONTINUE, JUnpooled.EMPTY_BUFFER)
  val res200Chunked  = new JDefaultHttpResponse(JHttpVersion.HTTP_1_1, JHttpResponseStatus.OK, chunkedHeaders)
  val res200         = new JDefaultHttpResponse(JHttpVersion.HTTP_1_1, JHttpResponseStatus.OK)
  val res404         = new JDefaultHttpResponse(JHttpVersion.HTTP_1_1, JHttpResponseStatus.NOT_FOUND)

  val health: Http[Any, Throwable, JHttpRequest, Channel[Any, Nothing, JHttpContent, JHttpObject]] =
    Http.collect[JHttpRequest] {
      case req if req.uri() == "/health" =>
        Channel.make[JHttpContent, JHttpObject] { case (Event.Register, context) =>
          context.write(res200) *>
            context.write(JLastHttpContent.EMPTY_LAST_CONTENT) *>
            context.flush
        }
    }

  val serverError: Http[Any, Throwable, JHttpRequest, Channel[Any, Nothing, JHttpContent, JHttpObject]] =
    Http.collectM[JHttpRequest] {
      case req if req.uri() == "/server-error" => ZIO.fail(new Error("What up?"))
    }

  val upload = Http.collect[JHttpRequest] {
    case req if req.uri() == "/upload" && req.method == JHttpMethod.POST =>
      Channel.make[JHttpContent, JHttpObject] {
        case (Event.Read(data: JLastHttpContent), context) => context.write(data) *> context.flush
        case (Event.Read(data: JHttpContent), context)     => context.write(data) *> context.flush *> context.read
        case (Event.Register, context)                     =>
          context.write(res100) *>
            context.flush *>
            context.write(res200Chunked) *>
            context.flush *>
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

  val notFound = Http.succeed {
    Channel.make[JHttpContent, JHttpObject] { case (_, context) =>
      context.write(res404) *>
        context.write(JLastHttpContent.EMPTY_LAST_CONTENT) *>
        context.flush
    }
  }

  val app = health +++ upload +++ serverError +++ notFound

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    Server.start0(8090, app).exitCode
  }
}
