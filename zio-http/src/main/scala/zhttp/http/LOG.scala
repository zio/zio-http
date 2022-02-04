package zhttp.http
import zio.Task

final case class LogStep(request: Request)

object LOG {
  import LogFmt._

  val DefaultLogConfig = request(method ++ url ++ headers ++ body) ++ response(status ++ headers ++ body) ++ duration
}

sealed trait LogLevel
object LogLevel {
  case object Error extends LogLevel
  case object Info  extends LogLevel
}

sealed trait LogFmt { self =>

  def ++(that: LogFmt): LogFmt = self combine that

  def combine(that: LogFmt): LogFmt = LogFmt.And(self, that)

  def run(response: Response, start: Long, end: Long): Task[String] = execute(
    false,
    self,
    method = None,
    url = None,
    response.headers,
    response.getBodyAsByteBuf.map(_.toString(HTTP_CHARSET)),
    Some(response.status),
    Some((end - start)),
  ).map(r => s"Response: $r")

  def run(request: Request): Task[String] = {
    execute(
      true,
      self,
      Some(request.method),
      Some(request.url),
      request.getHeaders,
      request.getBodyAsString,
      status = None,
      duration = None,
    ).map(r => s"Request: $r")
  }

  private def execute(
    isRequest: Boolean,
    logFmt: LogFmt,
    method: Option[Method],
    url: Option[URL],
    headers: Headers,
    body: Task[String],
    status: Option[Status],
    duration: Option[Long],
  ): Task[String] = {
    logFmt match {
      case LogFmt.And(left, right)               =>
        for {
          left  <- execute(isRequest, left, method, url, headers, body, status, duration)
          right <- execute(isRequest, right, method, url, headers, body, status, duration)
        } yield left ++ right
      case LogFmt.Method if method.isDefined     => Task.succeed(s" Method: ${method.get},")
      case LogFmt.Url if url.isDefined           => Task.succeed(s" Url: ${url.get.path.toString},")
      case LogFmt.Status if status.isDefined     => Task.succeed(s" Status: ${status.get},")
      case LogFmt.Duration if duration.isDefined => Task.succeed(s" Duration: ${duration.get}ms")
      case LogFmt.Body(limit)                    => body.map(b => s" Body: ${b.take(limit)}, ")
      case LogFmt.Request(fmt) if isRequest   => execute(isRequest, fmt, method, url, headers, body, status, duration)
      case LogFmt.Response(fmt) if !isRequest => execute(isRequest, fmt, method, url, headers, body, status, duration)
      case LogFmt.Headers(filter)             =>
        Task.succeed(s" Headers: ${LogFmt.stringifyHeaders(filter(headers)).mkString}")
      case _                                  => Task.succeed("")
    }
  }

}

object LogFmt {

  final case class And(left: LogFmt, right: LogFmt)                          extends LogFmt
  case object Status                                                         extends LogFmt
  case object Method                                                         extends LogFmt
  case object Url                                                            extends LogFmt
  case object Duration                                                       extends LogFmt
  final case class Body(limit: Int)                                          extends LogFmt
  final case class Request(fmt: LogFmt)                                      extends LogFmt
  final case class Response(fmt: LogFmt)                                     extends LogFmt
  final case class Headers(filter: zhttp.http.Headers => zhttp.http.Headers) extends LogFmt

  def status: LogFmt                                                               = Status
  def method: LogFmt                                                               = Method
  def url: LogFmt                                                                  = Url
  def body(limit: Int = 128)                                                       = Body(limit)
  def body                                                                         = Body(128)
  def duration: LogFmt                                                             = Duration
  def request(logFmt: LogFmt): LogFmt                                              = Request(logFmt)
  def response(logFmt: LogFmt): LogFmt                                             = Response(logFmt)
  def headers(filter: zhttp.http.Headers => zhttp.http.Headers = identity): LogFmt =
    Headers(filter)

  def headers = Headers(identity)

  private def stringifyHeaders(headers: zhttp.http.Headers): List[String] = headers.toList.map { case (name, value) =>
    s" $name = $value,"
  }
}
