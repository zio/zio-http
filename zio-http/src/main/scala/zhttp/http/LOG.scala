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

  def run(response: Response, start: Long, end: Long): String = {
    def execute(logFmt: LogFmt): String =
      logFmt match {
        case LogFmt.And(left, right)           => execute(left) ++ execute(right)
        case LogFmt.Status                     => s" Status: ${response.status},"
        case LogFmt.Duration                   => s" Duration: ${(end - start) / 1000}ms"
        case LogFmt.Response(fmt)              => s" Response: " ++ execute(fmt)
        case LogFmt.Headers(filter) =>
          s" Headers: ${LogFmt.stringifyHeaders(filter(response.headers)).mkString}"
        case _ => ""
      }

    execute(self)
  }

  def run(request: Request): String =  {
    def execute(logFmt: LogFmt): String =
      logFmt match {
        case LogFmt.And(left, right)           => execute(left) ++ execute(right)
        case LogFmt.Method                     => s" Method: ${request.method},"
        case LogFmt.Url                        => s" Url: ${request.url.asString},"
        case LogFmt.Request(fmt)               => s" Request: " ++ execute(fmt)
        case LogFmt.Headers(filter) =>
          s" Headers: ${LogFmt.stringifyHeaders(filter(request.getHeaders)).mkString}"
        case _ => ""
      }

    execute(self)
  }

}

object LogFmt {

  final case class And(left: LogFmt, right: LogFmt)                                              extends LogFmt
  case object Status                                                                             extends LogFmt
  case object Method                                                                             extends LogFmt
  case object Url                                                                                extends LogFmt
  case object Duration                                                                           extends LogFmt
  final case class Request(fmt: LogFmt)                                                          extends LogFmt
  final case class Response(fmt: LogFmt)                                                         extends LogFmt
  final case class Headers(filter: zhttp.http.Headers => zhttp.http.Headers) extends LogFmt

  def status: LogFmt                   = Status
  def method: LogFmt                   = Method
  def url: LogFmt                      = Url
  def duration: LogFmt                 = Duration
  def request(logFmt: LogFmt): LogFmt  = Request(logFmt)
  def response(logFmt: LogFmt): LogFmt = Response(logFmt)
  def headers(filter: zhttp.http.Headers => zhttp.http.Headers = identity): LogFmt =
    Headers(filter)

  def headers = Headers(identity)

  private def stringifyHeaders(headers: zhttp.http.Headers): List[String] = headers.toList.map { case (name, value) =>
    s" $name = $value,"
  }
}
