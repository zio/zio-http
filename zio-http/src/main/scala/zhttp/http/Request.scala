package zhttp.http

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

}

object Request {
  final case class Data(headers: List[Header], content: HttpContent[Any, String])
  object Data {
    val empty: Data = Data(Nil, HttpContent.Empty)
  }
}
