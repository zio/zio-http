package zhttp.http

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.HttpVersion
import zhttp.core.{JDefaultFullHttpRequest, JFullHttpRequest}
import zio.Task

// REQUEST
final case class Request(endpoint: Endpoint, data: Request.Data = Request.Data.empty) { self =>
  val headers: List[Header] = data.headers
  val method: Method        = endpoint._1
  val url: URL              = endpoint._2
  val route: Route          = method -> url.path

  def getBodyAsString: Option[String] = data.content match {
    case HttpContent.Complete(data) => Option(data)
    case _                          => Option.empty
  }

  def asJFullHttpRequest: Task[JFullHttpRequest] = Request.asJFullHttpRequest(self)
}

object Request {

  final case class Data(headers: List[Header], content: HttpContent[Any, String])
  object Data {
    val empty: Data = Data(Nil, HttpContent.Empty)
  }

  def asJFullHttpRequest(req: Request): Task[JFullHttpRequest] = Task {
    val method  = req.method.asJHttpMethod
    val uri     = req.url.asString
    val content = req.getBodyAsString match {
      case Some(text) => JUnpooled.copiedBuffer(text, HTTP_CHARSET)
      case None       => JUnpooled.EMPTY_BUFFER
    }
    val headers = Header.disassemble(req.headers)
    val jReq    = new JDefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, content)
    jReq.headers().set(headers)

    jReq
  }
}
