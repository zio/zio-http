/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.middleware

import java.nio.charset.{Charset, StandardCharsets}

import zio.{Exit, LogAnnotation, LogLevel, Trace, ZIO}

import zio.http._
import zio.http.model.Header.HeaderType
import zio.http.model.{Header, Status}

private[zio] trait RequestLogging { self: RequestHandlerMiddlewares =>

  final def requestLogging(
    level: Status => LogLevel = (_: Status) => LogLevel.Info,
    failureLevel: LogLevel = LogLevel.Warning,
    loggedRequestHeaders: Set[HeaderType] = Set.empty,
    loggedResponseHeader: Set[HeaderType] = Set.empty,
    logRequestBody: Boolean = false,
    logResponseBody: Boolean = false,
    requestCharset: Charset = StandardCharsets.UTF_8,
    responseCharset: Charset = StandardCharsets.UTF_8,
  )(implicit trace: Trace): RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    new RequestHandlerMiddleware.Simple[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        handler: Handler[R1, Err1, Request, Response],
      )(implicit trace: Trace): Handler[R1, Err1, Request, Response] =
        Handler.fromFunctionZIO { (request: Request) =>
          handler
            .runZIO(request)
            .sandbox
            .exit
            .timed
            .tap {
              case (duration, Exit.Success(response)) =>
                ZIO
                  .logLevel(level(response.status)) {
                    val requestHeaders  =
                      request.headers.collect {
                        case header: Header if loggedRequestHeaders.contains(header.headerType) =>
                          LogAnnotation(header.headerName, header.renderedValue)
                      }.toSet
                    val responseHeaders =
                      response.headers.collect {
                        case header: Header if loggedResponseHeader.contains(header.headerType) =>
                          LogAnnotation(header.headerName, header.renderedValue)
                      }.toSet

                    val requestBody  = if (request.body.isComplete) request.body.asChunk.option else ZIO.none
                    val responseBody = if (response.body.isComplete) response.body.asChunk.option else ZIO.none

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
                            LogAnnotation("status_code", response.status.text),
                            LogAnnotation("method", request.method.toString()),
                            LogAnnotation("url", request.url.encode),
                            LogAnnotation("duration_ms", duration.toMillis.toString),
                          ) union
                            requestHeaders union
                            responseHeaders union
                            bodyAnnotations,
                        ) {
                          ZIO.log("Http request served")
                        }
                      }
                    }
                  }
              case (duration, Exit.Failure(cause))    =>
                ZIO
                  .logLevel(failureLevel) {
                    val requestHeaders =
                      request.headers.collect {
                        case header: Header if loggedRequestHeaders.contains(header.headerType) =>
                          LogAnnotation(header.headerName, header.renderedValue)
                      }.toSet

                    val requestBody = if (request.body.isComplete) request.body.asChunk.option else ZIO.none

                    requestBody.flatMap { requestBodyChunk =>
                      val bodyAnnotations = Set(
                        requestBodyChunk.map(chunk => LogAnnotation("request_size", chunk.size.toString)),
                        requestBodyChunk.flatMap(chunk =>
                          if (logRequestBody)
                            Some(LogAnnotation("request", new String(chunk.toArray, requestCharset)))
                          else None,
                        ),
                      ).flatten

                      ZIO.logAnnotate(
                        Set(
                          LogAnnotation("method", request.method.toString()),
                          LogAnnotation("url", request.url.encode),
                          LogAnnotation("duration_ms", duration.toMillis.toString),
                        ) union
                          requestHeaders union
                          bodyAnnotations,
                      ) {
                        ZIO.logCause("Http request failed", cause)
                      }
                    }
                  }
            }
            .flatMap(_._2)
            .unsandbox
        }
    }
}
