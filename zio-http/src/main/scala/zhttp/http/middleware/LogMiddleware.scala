package zhttp.http.middleware

import java.io.IOException

import zio.{ZIO}
import zio.clock
import zio.clock.Clock
import zio.logging._
import zhttp.http._

object LogMiddleware {
  final case class RequestLogger(logMethod: Boolean, logHeaders: Boolean, level: LogLevel)
  final case class ResponseLogger(logMethod: Boolean, logHeaders: Boolean, level: LogLevel)

  sealed trait Options
  object Options {
    case object Default extends Options
    case class Skip(when: (RequestT, ResponseT) => Boolean) extends Options
  }

  type RequestT = (Method, URL, List[Header])
  type ResponseT = (Status, List[Header])
  type LogRequestStep = (LogBuilder, Long, RequestT)  // request log, start, request

  private def generateCorrelationId: String =
    java.lang.Long.toHexString(scala.util.Random.nextLong() | java.lang.Long.MIN_VALUE)

  private case class LogBuilder(id: String, prefix: String) {
    private val log = new StringBuilder()
    log.append(s"${prefix}[$id] ")

    def append[A](name: String, value: A) = ZIO.succeed(log.append(s"${name}=${value}, "))
    def appendIf[A](name: String, value: A, condition: Boolean) =
      if (condition) append(name, value)
      else ZIO.unit

    override def toString = log.toString()
  }

  private def doLogRequest(logDef: RequestLogger, req: RequestT) = {
    val (method, url, headers) = req
    val requestId = generateCorrelationId
    val logContent = LogBuilder(requestId, "Request")

    for {
      start <- clock.nanoTime
      _ <- logContent.append("Url", url.path)
      _ <- logContent.appendIf("Method", method, logDef.logMethod)
      _ <- logContent.appendIf("Headers", headers, logDef.logHeaders)
    } yield (logContent, start, (method, url, headers))
  }

  private def doLogResponse(responseOptions: ResponseLogger, options: Options, response: (Status, List[Header], LogRequestStep)) = {
    val (status, responseHeaders, prevData) = response
    val (requestLog, start, sourceReq) = prevData
    val (method, _, _) = sourceReq
    
    val logContent = LogBuilder(requestLog.id, "Response")

    def doLog = {
      for {
        _ <- zio.logging.log(responseOptions.level)(requestLog.toString)
        end <- clock.nanoTime
        _ <- logContent.append("Status", status)
        _ <- logContent.appendIf("Method", method, responseOptions.logMethod)
        _ <- logContent.appendIf("Headers", responseHeaders, responseOptions.logHeaders)
        _ <- logContent.append("Elapsed", (end - start) / 1000000)
        _ <- zio.logging.log(responseOptions.level)(logContent.toString)
      } yield Patch.empty
    }

    options match {
      case Options.Skip(filterFn) if !filterFn(sourceReq, (status, responseHeaders)) => ZIO.succeed(Patch.empty)
      case _ => doLog
    }
  }

  def log(request: RequestLogger, response: ResponseLogger, options: Options): HttpMiddleware[Clock with Logging, IOException] =
    HttpMiddleware.makeM
      { case r => doLogRequest(request, r) }
      { case r => doLogResponse(response, options, r) }
}