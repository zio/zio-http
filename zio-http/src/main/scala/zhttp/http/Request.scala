package zhttp.http

// REQUEST
final case class Request(endpoint: Endpoint, data: Request.Data = Request.Data.empty) { self =>
  val headers: List[Header] = data.headers
  val method: Method        = endpoint._1
  val url: URL              = endpoint._2
  val route: Route          = method -> url.path

  def getBodyAsString: Option[String] = data.content match {
    case HttpData.CompleteData(data) => Option(data.map(_.toChar).mkString)
    case _                           => Option.empty
  }

}

object Request {
  final case class Data(headers: List[Header], content: HttpData[Any, Nothing])
  object Data {
    val empty: Data = Data(Nil, HttpData.empty)
  }
}
