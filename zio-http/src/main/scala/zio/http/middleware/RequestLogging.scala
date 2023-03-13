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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Clock, LogAnnotation, LogLevel, Trace, ZIO}

import zio.http._
import zio.http.model.Headers.Header
import zio.http.model.Status

private[zio] trait RequestLogging { self: RequestHandlerMiddlewares =>

  final def requestLogging(
    level: Status => LogLevel = (_: Status) => LogLevel.Info,
    loggedRequestHeaders: Set[String] = Set.empty,
    loggedResponseHeader: Set[String] = Set.empty,
    logRequestBody: Boolean = false,
    logResponseBody: Boolean = false,
    requestCharset: Charset = StandardCharsets.UTF_8,
    responseCharset: Charset = StandardCharsets.UTF_8,
  )(implicit trace: Trace): RequestHandlerMiddleware[Any, Throwable] =
    interceptPatchZIO { request =>
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
                    LogAnnotation("status_code", response.status.text),
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
      } yield patch
    }

}
