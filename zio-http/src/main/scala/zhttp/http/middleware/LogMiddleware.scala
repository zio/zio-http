package zhttp.http.middleware

import java.io.IOException

import zio.{ZIO}
import zio.clock
import zio.clock.Clock
import zio.logging._
import zhttp.http._

object LogMiddleware {

  /**
   * Configures the way the request is displayed
   * @param logMethod
   *   Should the HTTP method be logged
   * @param logHeaders
   *   Determines if the HTTP request headers are logged
   * @param mapHeaders
   *   It allows to change the display message or filter headers. Defaults to identity function.
   * @param level
   *   LogLevel
   */
  final case class RequestLogger(
    logMethod: Boolean,
    logHeaders: Boolean,
    mapHeaders: (List[Header]) => List[Header] = identity,
    level: LogLevel = LogLevel.Debug,
  )

  /**
   * Configures the way the response is displayed
   * @param logMethod
   *   Should the HTTP method be logged
   * @param logHeaders
   *   Determines if the HTTP response headers are logged
   * @param mapHeaders
   *   It allows to change the display message or filter headers. Defaults to identity function.
   * @param level
   *   LogLevel
   */
  final case class ResponseLogger(
    logMethod: Boolean,
    logHeaders: Boolean,
    mapHeaders: (List[Header]) => List[Header] = identity,
    level: LogLevel = LogLevel.Debug,
  )

  /**
   * Basic options of the log middleware Skip(when): determines when a request should be logged
   */
  sealed trait Options
  object Options {
    case object Default                                     extends Options
    case class Skip(when: (RequestT, ResponseT) => Boolean) extends Options
  }

  private type RequestT       = (Method, URL, List[Header])
  private type ResponseT      = (Status, List[Header])
  private type LogRequestStep = (LogBuilder, Long, RequestT) // request log, start, request

  private def generateCorrelationId: String =
    java.lang.Long.toHexString(scala.util.Random.nextLong() | java.lang.Long.MIN_VALUE)

  private case class LogBuilder(id: String, prefix: String) {
    private val log = new StringBuilder()
    log.append(s"${prefix}[$id]")

    def append[A](name: String, value: A, units: String = ""): zio.UIO[Any]                       =
      ZIO.succeed(log.append(s", ${name}=${value}${units}"))
    def appendIf[A](name: String, value: A, condition: Boolean, units: String = ""): zio.UIO[Any] =
      if (condition) append(name, value, units)
      else ZIO.unit

    override def toString: String = log.toString()
  }

  private def doLogRequest(requestOptions: RequestLogger, req: RequestT): ZIO[Clock, Nothing, LogRequestStep] = {
    val (method, url, headers) = req
    val requestId              = generateCorrelationId
    val logContent             = LogBuilder(requestId, "Request")

    val filteredHeaders = requestOptions.mapHeaders(headers)

    for {
      start <- clock.nanoTime
      _     <- logContent.append("Url", url.path)
      _     <- logContent.appendIf("Method", method, requestOptions.logMethod)
      _     <- logContent.appendIf("Headers", filteredHeaders, requestOptions.logHeaders)
    } yield (logContent, start, (method, url, headers))
  }

  private def doLogResponse(
    responseOptions: ResponseLogger,
    options: Options,
    response: (Status, List[Header], LogRequestStep),
  ): ZIO[Logging with Clock, Nothing, Patch] = {
    val (status, responseHeaders, prevData) = response
    val (requestLog, start, sourceReq)      = prevData
    val (method, _, _)                      = sourceReq

    val logContent = LogBuilder(requestLog.id, "Response")

    val filteredHeaders = responseOptions.mapHeaders(responseHeaders)

    def doLog = {
      for {
        _   <- zio.logging.log(responseOptions.level)(requestLog.toString)
        end <- clock.nanoTime
        _   <- logContent.append("Status", status)
        _   <- logContent.append("Elapsed", (end - start) / 1000000, "ms")
        _   <- logContent.appendIf("Method", method, responseOptions.logMethod)
        _   <- logContent.appendIf("Headers", filteredHeaders, responseOptions.logHeaders)
        _   <- zio.logging.log(responseOptions.level)(logContent.toString)
      } yield Patch.empty
    }

    options match {
      case Options.Skip(excludeFn) if excludeFn(sourceReq, (status, responseHeaders)) => ZIO.succeed(Patch.empty)
      case _                                                                          => doLog
    }
  }

  /**
   * Creates a log middleware with different options for logging request/response info.
   */
  def log(
    request: RequestLogger,
    response: ResponseLogger,
    options: Options,
  ): HttpMiddleware[Clock with Logging, IOException] =
    HttpMiddleware.makeM { case r => doLogRequest(request, r) } { case r => doLogResponse(response, options, r) }
}
