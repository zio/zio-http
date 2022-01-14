package zhttp.http

final case class LogStep(request: Request)

object LOG {
  import LogFmt._

  val DefaultLogConfig = request(method ++ url ++ headers) ++ response(status ++ headers) ++ duration
}

sealed trait LogLevel
object LogLevel {
  case object Error extends LogLevel
  case object Info  extends LogLevel
}

sealed trait LogFmt { self =>

  def ++(that: LogFmt): LogFmt = self combine that

  def combine(that: LogFmt): LogFmt = LogFmt.And(self, that)

  def run(request: Request, response: Response, start: Long, end: Long): String = {
    def execute(logFmt: LogFmt): String =
      logFmt match {
        case LogFmt.And(left, right)           => execute(left) ++ execute(right)
        case LogFmt.Status                     => s" Status: ${response.status}"
        case LogFmt.Method                     => s"  Method: ${request.method}"
        case LogFmt.Url                        => s" Url: ${request.url.asString}"
        case LogFmt.Duration                   => s" Duration: ${(end - start) / 1000}ms"
        case LogFmt.Request(fmt)               => s"\nRequest: " ++ execute(fmt)
        case LogFmt.Response(fmt)              => s"\nResponse: " ++ execute(fmt)
        case LogFmt.Headers(filter, isRequest) =>
          s" Headers: ${LogFmt.stringifyHeaders(filter(if (isRequest) request.getHeaders else response.headers)).mkString("\n")}"
      }

    execute(self)
  }

}

object LogFmt {

  final case class And(left: LogFmt, right: LogFmt)                                              extends LogFmt
  final case object Status                                                                       extends LogFmt
  final case object Method                                                                       extends LogFmt
  final case object Url                                                                          extends LogFmt
  final case object Duration                                                                     extends LogFmt
  final case class Request(fmt: LogFmt)                                                          extends LogFmt
  final case class Response(fmt: LogFmt)                                                         extends LogFmt
  final case class Headers(filter: zhttp.http.Headers => zhttp.http.Headers, isRequest: Boolean) extends LogFmt

  def status: LogFmt                   = Status
  def method: LogFmt                   = Method
  def url: LogFmt                      = Url
  def duration: LogFmt                 = Duration
  def request(logFmt: LogFmt): LogFmt  = Request(logFmt)
  def response(logFmt: LogFmt): LogFmt = Response(logFmt)
  def headers(filter: zhttp.http.Headers => zhttp.http.Headers = identity, isRequest: Boolean = true): LogFmt =
    Headers(filter, isRequest)

  def headers = Headers(identity, true)

  private def stringifyHeaders(headers: zhttp.http.Headers): List[String] = headers.toList.map { case (name, value) =>
    s"  $name = $value \n"
  }
}
