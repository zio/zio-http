package zio.http.middleware

import zio.http._
import zio.http.model.Headers.Header
import zio.http.model.Status
import zio.{Clock, LogAnnotation, LogLevel, Trace, ZIO}

import java.nio.charset.{Charset, StandardCharsets}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] trait RequestLogging {

  final def requestLogging(
    level: Status => LogLevel = (_: Status) => LogLevel.Info,
    loggedRequestHeaders: Set[String] = Set.empty,
    loggedResponseHeader: Set[String] = Set.empty,
    logRequestBody: Boolean = false,
    logResponseBody: Boolean = false,
    requestCharset: Charset = StandardCharsets.UTF_8,
    responseCharset: Charset = StandardCharsets.UTF_8,
  )(implicit trace: Trace): HttpMiddleware[Any, Throwable] =
    Middleware.interceptZIOPatch { request =>
      Clock.nanoTime.map(start => (request, start))
    } { case (response, (request, start)) =>
      for {
        end <- Clock.nanoTime
        durationMs = (end - start) / 1000000
        patch <- ZIO
          .logLevel(level(response.status)) {
            val requestHeaders  =
              request.headers.toList.collect {
                case Header(name, value) if loggedRequestHeaders.contains(name.toString) =>
                  LogAnnotation(name.toString, value.toString)
              }.toSet
            val responseHeaders =
              response.headers.toList.collect {
                case Header(name, value) if loggedResponseHeader.contains(name.toString) =>
                  LogAnnotation(name.toString, value.toString)
              }.toSet

            val requestBody  = if (request.body.isComplete) request.body.asChunk.map(Some(_)) else ZIO.none
            val responseBody = if (response.body.isComplete) response.body.asChunk.map(Some(_)) else ZIO.none

            requestBody.flatMap { requestBodyChunk =>
              responseBody.flatMap { responseBodyChunk =>
                val bodyAnnotations = Set(
                  requestBodyChunk.map(chunk => LogAnnotation("request_size", chunk.size.toString)),
                  requestBodyChunk.flatMap(chunk =>
                    if (logRequestBody)
                      Some(LogAnnotation("request", new String(chunk.toArray, requestCharset)))
                    else None,
                  ),
                  responseBodyChunk.map(chunk => LogAnnotation("response_size", chunk.size.toString)),
                  responseBodyChunk.flatMap(chunk =>
                    if (logResponseBody)
                      Some(LogAnnotation("response", new String(chunk.toArray, responseCharset)))
                    else None,
                  ),
                ).flatten

                ZIO.logAnnotate(
                  Set(
                    LogAnnotation("status_code", response.status.asJava.code().toString),
                    LogAnnotation("method", request.method.toString()),
                    LogAnnotation("url", request.url.encode),
                    LogAnnotation("duration_ms", durationMs.toString),
                  ) union
                    requestHeaders union
                    responseHeaders union
                    bodyAnnotations,
                ) {
                  ZIO
                    .log("Http request served")
                    .as(Patch.empty)
                }
              }
            }
          }
          .mapError(Option(_))
      } yield patch
    }

}
