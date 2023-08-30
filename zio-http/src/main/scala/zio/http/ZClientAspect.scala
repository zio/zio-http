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

package zio.http
import java.nio.charset.{Charset, StandardCharsets}

import zio._

import zio.http.Header.HeaderType

/**
 * A `ZClientAspect` is capable on modifying some aspect of the execution of a
 * client, such as metrics, tracing, encoding, decoding, or logging.
 */
trait ZClientAspect[+LowerEnv, -UpperEnv, +LowerIn, -UpperIn, +LowerErr, -UpperErr, +LowerOut, -UpperOut] { self =>

  /**
   * Applies this aspect to modify the execution of the specified client.
   */
  def apply[
    Env >: LowerEnv <: UpperEnv,
    In >: LowerIn <: UpperIn,
    Err >: LowerErr <: UpperErr,
    Out >: LowerOut <: UpperOut,
  ](client: ZClient[Env, In, Err, Out]): ZClient[Env, In, Err, Out]

  /**
   * Composes this client aspect with the specified client aspect to return a
   * new client aspect that applies the modifications of this client aspect and
   * then the modifications of that client aspect.
   */
  final def @@[
    LowerEnv1 >: LowerEnv,
    UpperEnv1 <: UpperEnv,
    LowerIn1 >: LowerIn,
    UpperIn1 <: UpperIn,
    LowerErr1 >: LowerErr,
    UpperErr1 <: UpperErr,
    LowerOut1 >: LowerOut,
    UpperOut1 <: UpperOut,
  ](
    that: ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1],
  ): ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1] =
    self.andThen(that)

  /**
   * Composes this client aspect with the specified client aspect to return a
   * new client aspect that applies the modifications of this client aspect and
   * then the modifications of that client aspect.
   */
  final def >>>[
    LowerEnv1 >: LowerEnv,
    UpperEnv1 <: UpperEnv,
    LowerIn1 >: LowerIn,
    UpperIn1 <: UpperIn,
    LowerErr1 >: LowerErr,
    UpperErr1 <: UpperErr,
    LowerOut1 >: LowerOut,
    UpperOut1 <: UpperOut,
  ](
    that: ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1],
  ): ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1] =
    self.andThen(that)

  /**
   * Composes this client aspect with the specified client aspect to return a
   * new client aspect that applies the modifications of this client aspect and
   * then the modifications of that client aspect.
   */
  final def andThen[
    LowerEnv1 >: LowerEnv,
    UpperEnv1 <: UpperEnv,
    LowerIn1 >: LowerIn,
    UpperIn1 <: UpperIn,
    LowerErr1 >: LowerErr,
    UpperErr1 <: UpperErr,
    LowerOut1 >: LowerOut,
    UpperOut1 <: UpperOut,
  ](
    that: ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1],
  ): ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1] =
    new ZClientAspect[LowerEnv1, UpperEnv1, LowerIn1, UpperIn1, LowerErr1, UpperErr1, LowerOut1, UpperOut1] {
      override def apply[
        Env >: LowerEnv1 <: UpperEnv1,
        In >: LowerIn1 <: UpperIn1,
        Err >: LowerErr1 <: UpperErr1,
        Out >: LowerOut1 <: UpperOut1,
      ](client: ZClient[Env, In, Err, Out]): ZClient[Env, In, Err, Out] =
        that(self(client))
    }
}

object ZClientAspect {

  /**
   * Client aspect that logs a debug message to the console after each request
   */
  final def debug(implicit trace: Trace): ZClientAspect[Nothing, Any, Nothing, Body, Nothing, Any, Nothing, Response] =
    debug(PartialFunction.empty)

  /**
   * Client aspect that logs a debug message to the console after each request
   */
  final def debug(
    extraMessage: PartialFunction[Response, String],
  )(implicit trace: Trace): ZClientAspect[Nothing, Any, Nothing, Body, Nothing, Any, Nothing, Response] =
    new ZClientAspect[Nothing, Any, Nothing, Body, Nothing, Any, Nothing, Response] {

      /**
       * Applies this aspect to modify the execution of the specified client.
       */
      override def apply[
        Env >: Nothing <: Any,
        In >: Nothing <: Body,
        Err >: Nothing <: Any,
        Out >: Nothing <: Response,
      ](
        client: ZClient[Env, In, Err, Out],
      ): ZClient[Env, In, Err, Out] = {
        val oldDriver = client.driver

        val newDriver = new ZClient.Driver[Env, Err] {
          override def request(
            version: Version,
            method: Method,
            url: URL,
            headers: Headers,
            body: Body,
            sslConfig: Option[ClientSSLConfig],
            proxy: Option[Proxy],
          )(implicit trace: Trace): ZIO[Env & Scope, Err, Response] =
            oldDriver
              .request(version, method, url, headers, body, sslConfig, proxy)
              .sandbox
              .exit
              .timed
              .tap {
                case (duration, Exit.Success(response)) =>
                  {
                    Console.printLine(s"${response.status.code} $method ${url.encode} ${duration.toMillis}ms") *>
                      Console.printLine(extraMessage(response)).when(extraMessage.isDefinedAt(response))
                  }.orDie
                case (duration, Exit.Failure(cause))    =>
                  Console
                    .printLine(
                      s"Failed $method ${url.encode} ${duration.toMillis}ms: " + cause.prettyPrint,
                    )
                    .orDie
              }
              .flatMap(_._2)
              .unsandbox

          override def socket[Env1 <: Env](version: Version, url: URL, headers: Headers, app: WebSocketApp[Env1])(
            implicit trace: Trace,
          ): ZIO[Env1 with Scope, Err, Response] =
            client.driver.socket(version, url, headers, app)
        }

        client.transform(client.bodyEncoder, client.bodyDecoder, newDriver)
      }
    }

  /**
   * Client aspect that logs details of each request and response
   */
  final def requestLogging(
    level: Status => LogLevel = (_: Status) => LogLevel.Info,
    failureLevel: LogLevel = LogLevel.Warning,
    loggedRequestHeaders: Set[HeaderType] = Set.empty,
    loggedResponseHeaders: Set[HeaderType] = Set.empty,
    logRequestBody: Boolean = false,
    logResponseBody: Boolean = false,
    requestCharset: Charset = StandardCharsets.UTF_8,
    responseCharset: Charset = StandardCharsets.UTF_8,
  )(implicit trace: Trace): ZClientAspect[Nothing, Any, Nothing, Body, Nothing, Any, Nothing, Response] = {
    val loggedRequestHeaderNames  = loggedRequestHeaders.map(_.name.toLowerCase)
    val loggedResponseHeaderNames = loggedResponseHeaders.map(_.name.toLowerCase)
    new ZClientAspect[Nothing, Any, Nothing, Body, Nothing, Any, Nothing, Response] {

      /**
       * Applies this aspect to modify the execution of the specified client.
       */
      override def apply[
        Env >: Nothing <: Any,
        In >: Nothing <: Body,
        Err >: Nothing <: Any,
        Out >: Nothing <: Response,
      ](
        client: ZClient[Env, In, Err, Out],
      ): ZClient[Env, In, Err, Out] = {
        val oldDriver = client.driver

        val newDriver = new ZClient.Driver[Env, Err] {
          override def request(
            version: Version,
            method: Method,
            url: URL,
            headers: Headers,
            body: Body,
            sslConfig: Option[ClientSSLConfig],
            proxy: Option[Proxy],
          )(implicit trace: Trace): ZIO[Env & Scope, Err, Response] = {
            oldDriver
              .request(version, method, url, headers, body, sslConfig, proxy)
              .sandbox
              .exit
              .timed
              .tap {
                case (duration, Exit.Success(response)) =>
                  ZIO
                    .logLevel(level(response.status)) {
                      def requestHeaders  =
                        headers.collect {
                          case header: Header if loggedRequestHeaderNames.contains(header.headerName.toLowerCase) =>
                            LogAnnotation(header.headerName, header.renderedValue)
                        }.toSet
                      def responseHeaders =
                        response.headers.collect {
                          case header: Header if loggedResponseHeaderNames.contains(header.headerName.toLowerCase) =>
                            LogAnnotation(header.headerName, header.renderedValue)
                        }.toSet

                      val requestBody  = if (body.isComplete) body.asChunk.option else ZIO.none
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
                              LogAnnotation("method", method.toString()),
                              LogAnnotation("url", url.encode),
                              LogAnnotation("duration_ms", duration.toMillis.toString),
                            ) union
                              requestHeaders union
                              responseHeaders union
                              bodyAnnotations,
                          ) {
                            ZIO.log("Http client request")
                          }
                        }
                      }
                    }
                case (duration, Exit.Failure(cause))    =>
                  ZIO
                    .logLevel(failureLevel) {
                      val requestHeaders =
                        headers.collect {
                          case header: Header if loggedRequestHeaderNames.contains(header.headerName.toLowerCase) =>
                            LogAnnotation(header.headerName, header.renderedValue)
                        }.toSet

                      val requestBody = if (body.isComplete) body.asChunk.option else ZIO.none

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
                            LogAnnotation("method", method.toString()),
                            LogAnnotation("url", url.encode),
                            LogAnnotation("duration_ms", duration.toMillis.toString),
                          ) union
                            requestHeaders union
                            bodyAnnotations,
                        ) {
                          ZIO.logCause("Http client request failed", cause)
                        }
                      }
                    }
              }
              .flatMap(_._2)
              .unsandbox
          }

          override def socket[Env1 <: Env](version: Version, url: URL, headers: Headers, app: WebSocketApp[Env1])(
            implicit trace: Trace,
          ): ZIO[Env1 with Scope, Err, Response] =
            client.driver.socket(version, url, headers, app)
        }

        client.transform(client.bodyEncoder, client.bodyDecoder, newDriver)
      }
    }
  }
}
