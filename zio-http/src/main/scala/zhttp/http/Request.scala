package zhttp.http

// REQUEST
final case class Request(
  endpoint: Endpoint,
  headers: List[Header] = List.empty,
  content: HttpData[Any, Nothing] = HttpData.empty,
) extends HasHeaders
    with HeadersHelpers { self =>
  val method: Method = endpoint._1
  val url: URL       = endpoint._2
  val route: Route   = method -> url.path

  def getBodyAsString: Option[String] = content match {
    case HttpData.CompleteData(data) => Option(data.map(_.toChar).mkString)
    case _                           => Option.empty
  }
}

object Request {}
