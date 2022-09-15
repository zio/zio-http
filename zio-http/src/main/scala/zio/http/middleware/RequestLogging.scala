package zio.http.middleware

import zio.http.{Middleware, Patch, Status}
import zio.{Clock, LogAnnotation, LogLevel, ZIO}

import java.nio.charset.{Charset, StandardCharsets}

private[zio] trait RequestLogging {

  final def requestLogging(
    level: Status => LogLevel = (_: Status) => LogLevel.Info,
    loggedRequestHeaders: Set[String] = Set.empty,
    loggedResponseHeader: Set[String] = Set.empty,
    logRequestBody: Boolean = false,
    logResponseBody: Boolean = false,
    requestCharset: Charset = StandardCharsets.UTF_8,
    responseCharset: Charset = StandardCharsets.UTF_8,
  ): HttpMiddleware[Any, Throwable] =
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
                case (name, value) if loggedRequestHeaders.contains(name) => LogAnnotation(name, value)
              }.toSet
            val responseHeaders =
              response.headers.toList.collect {
                case (name, value) if loggedResponseHeader.contains(name) => LogAnnotation(name, value)
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
