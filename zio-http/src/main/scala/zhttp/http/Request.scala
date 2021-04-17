package zhttp.http

import zio.Chunk

// REQUEST
final case class Request(endpoint: Endpoint, data: Request.Data = Request.Data.empty) { self =>
  val headers: List[Header] = data.headers
  val method: Method        = endpoint._1
  val url: URL              = endpoint._2
  val route: Route          = method -> url.path

  def getBodyAsString: Option[String] = data.content match {
    case HttpContent.Complete(data) => Option(data.map(_.toChar).mkString)
    case _                          => Option.empty
  }

}

object Request {
  val emptyContent: HttpContent.Complete[Byte] = HttpContent.Complete(Chunk.empty)
  final case class Data(headers: List[Header], content: HttpContent[Any, Byte])
  object Data {
    val empty: Data = Data(Nil, emptyContent)
  }
}
