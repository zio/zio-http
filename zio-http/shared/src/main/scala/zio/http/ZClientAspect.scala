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
 * A `ZClientAspect` is capable of modifying some aspect of the execution of a
 * client, such as metrics, tracing, encoding, decoding, or logging. Apply it to
 * a [[ZClient]] with `client @@ aspect`.
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/client/ Client]]
 */
trait ZClientAspect[+LowerEnv, -UpperEnv, +LowerIn, -UpperIn, +LowerErr, -UpperErr, +LowerOut, -UpperOut] { self =>

  /**
   * Applies this aspect to modify the execution of the specified client.
   */
  def apply[
    ReqEnv,
    Env >: LowerEnv <: UpperEnv,
    In >: LowerIn <: UpperIn,
    Err >: LowerErr <: UpperErr,
    Out >: LowerOut <: UpperOut,
  ](client: ZClient[Env, ReqEnv, In, Err, Out]): ZClient[Env, ReqEnv, In, Err, Out]

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
        ReqEnv,
        Env >: LowerEnv1 <: UpperEnv1,
        In >: LowerIn1 <: UpperIn1,
        Err >: LowerErr1 <: UpperErr1,
        Out >: LowerOut1 <: UpperOut1,
      ](client: ZClient[Env, ReqEnv, In, Err, Out]): ZClient[Env, ReqEnv, In, Err, Out] =
        that(self(client))
    }
}

/**
 * Client aspects for logging (`debug`, `requestLogging`, `curlLogger`),
 * redirects (`followRedirects`) and header forwarding (`forwardHeaders`).
 */
object ZClientAspect {

  /**
   * Client aspect that prints a line to the console after each request, with
   * the status, method, URL and duration, or the failure.
   */
  final def debug(implicit trace: Trace): ZClientAspect[Nothing, Any, Nothing, Body, Nothing, Any, Nothing, Response] =
    debug(PartialFunction.empty)

  /**
   * Client aspect that prints a line to the console after each request, with
   * the status, method, URL and duration, or the failure. For a response where
   * `extraMessage` is defined, it also prints the message that `extraMessage`
   * returns.
   */
  final def debug(
    extraMessage: PartialFunction[Response, String],
  )(implicit trace: Trace): ZClientAspect[Nothing, Any, Nothing, Body, Nothing, Any, Nothing, Response] =
    new ZClientAspect[Nothing, Any, Nothing, Body, Nothing, Any, Nothing, Response] {

      /**
       * Applies this aspect to modify the execution of the specified client.
       */
      override def apply[
        ReqEnv,
        Env >: Nothing <: Any,
        In >: Nothing <: Body,
        Err >: Nothing <: Any,
        Out >: Nothing <: Response,
      ](
        client: ZClient[Env, ReqEnv, In, Err, Out],
      ): ZClient[Env, ReqEnv, In, Err, Out] = {
        val oldDriver = client.driver

        val newDriver = new ZClient.Driver[Env, ReqEnv, Err] {
          override def request(
            version: Version,
            method: Method,
            url: URL,
            headers: Headers,
            body: Body,
            sslConfig: Option[ClientSSLConfig],
            proxy: Option[Proxy],
          )(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response] =
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
            implicit
            trace: Trace,
            ev: ReqEnv =:= Scope,
          ): ZIO[Env1 & ReqEnv, Err, Response] =
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
        ReqEnv,
        Env >: Nothing <: Any,
        In >: Nothing <: Body,
        Err >: Nothing <: Any,
        Out >: Nothing <: Response,
      ](
        client: ZClient[Env, ReqEnv, In, Err, Out],
      ): ZClient[Env, ReqEnv, In, Err, Out] = {
        val oldDriver = client.driver

        val newDriver = new ZClient.Driver[Env, ReqEnv, Err] {
          override def request(
            version: Version,
            method: Method,
            url: URL,
            headers: Headers,
            body: Body,
            sslConfig: Option[ClientSSLConfig],
            proxy: Option[Proxy],
          )(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response] = {
            oldDriver
              .request(version, method, url, headers, body, sslConfig, proxy)
              .sandbox
              .exit
              .timed
              .tap {
                case (duration, Exit.Success(response)) =>
                  ZIO
                    .logLevel(level(response.status)) {
                      def requestHeaders =
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
            implicit
            trace: Trace,
            ev: ReqEnv =:= Scope,
          ): ZIO[Env1 & ReqEnv, Err, Response] =
            client.driver.socket(version, url, headers, app)
        }

        client.transform(client.bodyEncoder, client.bodyDecoder, newDriver)
      }
    }
  }

  /**
   * Client aspect that follows redirect responses (status 3xx) up to `max`
   * times, sending the same method, headers and body to the URL of the
   * `Location` header, resolved against the request URL. The response of
   * `onRedirectError` is returned instead when a redirect has no valid
   * `Location` header, or when `max` redirects have been followed and the next
   * response is a redirect too; its second argument is the reason, such as
   * "followed maximum redirects". WebSocket connections follow redirects the
   * same way.
   */
  final def followRedirects[R, E](max: Int)(
    onRedirectError: (Response, String) => ZIO[R, E, Response],
  )(implicit trace: Trace): ZClientAspect[Nothing, R, Nothing, Body, E, Any, Nothing, Response] = {
    new ZClientAspect[Nothing, R, Nothing, Body, E, Any, Nothing, Response] {
      override def apply[
        ReqEnv,
        Env >: Nothing <: R,
        In >: Nothing <: Body,
        Err >: E <: Any,
        Out >: Nothing <: Response,
      ](client: ZClient[Env, ReqEnv, In, Err, Out]): ZClient[Env, ReqEnv, In, Err, Out] = {
        val oldDriver = client.driver

        val newDriver = new ZClient.Driver[Env, ReqEnv, Err] {
          def scopedRedirectErr(resp: Response, message: String) =
            ZIO.suspendSucceed(onRedirectError(resp, message))

          override def request(
            version: Version,
            method: Method,
            url: URL,
            headers: Headers,
            body: Body,
            sslConfig: Option[ClientSSLConfig],
            proxy: Option[Proxy],
          )(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response] = {
            def req(
              attempt: Int,
              version: Version,
              method: Method,
              url: URL,
              headers: Headers,
              body: Body,
              sslConfig: Option[ClientSSLConfig],
              proxy: Option[Proxy],
            ): ZIO[Env & ReqEnv, Err, Response] = {
              oldDriver.request(version, method, url, headers, body, sslConfig, proxy).flatMap { resp =>
                if (resp.status.isRedirection) {
                  if (attempt < max) {
                    resp.headerOrFail(Header.Location) match {
                      case Some(locOrError) =>
                        locOrError match {
                          case Left(locHeaderErr) =>
                            scopedRedirectErr(resp, locHeaderErr)

                          case Right(loc) =>
                            url.resolve(loc.url) match {
                              case Left(relativeResolveErr) =>
                                scopedRedirectErr(resp, relativeResolveErr)

                              case Right(resolved) =>
                                req(attempt + 1, version, method, resolved, headers, body, sslConfig, proxy)
                            }
                        }
                      case None             =>
                        scopedRedirectErr(resp, "no location header to resolve redirect")
                    }
                  } else {
                    scopedRedirectErr(resp, "followed maximum redirects")
                  }
                } else {
                  ZIO.succeed(resp)
                }
              }
            }

            req(0, version, method, url, headers, body, sslConfig, proxy)
          }

          override def socket[Env1 <: Env](version: Version, url: URL, headers: Headers, app: WebSocketApp[Env1])(
            implicit
            trace: Trace,
            ev: ReqEnv =:= Scope,
          ): ZIO[Env1 & ReqEnv, Err, Response] = {
            def sock(
              attempt: Int,
              version: Version,
              url: URL,
              headers: Headers,
              app: WebSocketApp[Env1],
            ): ZIO[Env1 & ReqEnv, Err, Response] = {
              oldDriver.socket(version, url, headers, app).flatMap { resp =>
                if (resp.status.isRedirection) {
                  if (attempt < max) {
                    resp.headerOrFail(Header.Location) match {
                      case Some(locOrError) =>
                        locOrError match {
                          case Left(locHeaderErr) =>
                            scopedRedirectErr(resp, locHeaderErr)

                          case Right(loc) =>
                            url.resolve(loc.url) match {
                              case Left(relativeResolveErr) =>
                                scopedRedirectErr(resp, relativeResolveErr)

                              case Right(resolved) =>
                                sock(attempt + 1, version, resolved, headers, app)
                            }
                        }
                      case None             =>
                        scopedRedirectErr(resp, "no location header to resolve redirect")
                    }
                  } else {
                    scopedRedirectErr(resp, "followed maximum redirects")
                  }
                } else {
                  ZIO.succeed(resp)
                }
              }
            }

            sock(0, version, url, headers, app)
          }
        }

        client.transform(client.bodyEncoder, client.bodyDecoder, newDriver)
      }
    }
  }

  /**
   * Client aspect that adds to each request the headers that
   * `Middleware.forwardHeaders` saved from the incoming server request, when
   * the client runs inside a handler of routes with that middleware.
   */
  final def forwardHeaders: ZClientAspect[Nothing, Any, Nothing, Body, Nothing, Any, Nothing, Response] =
    new ZClientAspect[Nothing, Any, Nothing, Body, Nothing, Any, Nothing, Response] {
      override def apply[
        ReqEnv,
        Env >: Nothing <: Any,
        In >: Nothing <: Body,
        Err >: Nothing <: Any,
        Out >: Nothing <: Response,
      ](
        client: ZClient[Env, ReqEnv, In, Err, Out],
      ): ZClient[Env, ReqEnv, In, Err, Out] =
        client.copy(
          driver = new ZClient.Driver[Env, ReqEnv, Err] {
            override def request(
              version: Version,
              method: Method,
              url: URL,
              headers: Headers,
              body: Body,
              sslConfig: Option[ClientSSLConfig],
              proxy: Option[Proxy],
            )(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response] =
              RequestStore.get[Middleware.ForwardedHeaders].flatMap {
                case Some(forwardedHeaders) =>
                  client.driver
                    .request(version, method, url, headers ++ forwardedHeaders.headers, body, sslConfig, proxy)
                case None                   =>
                  client.driver.request(version, method, url, headers, body, sslConfig, proxy)
              }

            override def socket[Env1 <: Env](version: Version, url: URL, headers: Headers, app: WebSocketApp[Env1])(
              implicit
              trace: Trace,
              ev: ReqEnv =:= Scope,
            ): ZIO[Env1 & ReqEnv, Err, Response] =
              RequestStore.get[Middleware.ForwardedHeaders].flatMap {
                case Some(forwardedHeaders) =>
                  client.driver.socket(version, url, headers ++ forwardedHeaders.headers, app)
                case None                   =>
                  client.driver.socket(version, url, headers, app)
              }
          },
        )
    }

  /**
   * Client aspect that logs each request as a curl command with `logEffect` (by
   * default `ZIO.log`) before sending it. The command has `--verbose` when
   * `verbose` is true, and includes the body only when the body is complete
   * (`Body#isComplete`).
   */
  final def curlLogger(
    verbose: Boolean = true,
    logEffect: String => UIO[Unit] = (m: String) => ZIO.log(m),
  ): ZClientAspect[Nothing, Any, Nothing, Body, Throwable, Throwable, Nothing, Response] =
    new ZClientAspect[Nothing, Any, Nothing, Body, Throwable, Throwable, Nothing, Response] {

      /**
       * Applies this aspect to modify the execution of the specified client.
       */
      override def apply[
        ReqEnv,
        Env >: Nothing <: Any,
        In >: Nothing <: Body,
        Err >: Throwable <: Throwable,
        Out >: Nothing <: Response,
      ](
        client: ZClient[Env, ReqEnv, In, Err, Out],
      ): ZClient[Env, ReqEnv, In, Err, Out] = {
        val oldDriver = client.driver

        val newDriver = new ZClient.Driver[Env, ReqEnv, Err] {
          override def request(
            version: Version,
            method: Method,
            url: URL,
            headers: Headers,
            body: Body,
            sslConfig: Option[ClientSSLConfig],
            proxy: Option[Proxy],
          )(implicit trace: Trace): ZIO[Env & ReqEnv, Err, Response] =
            CurlLogger.formatCurlCommand(version, method, url, headers, body, proxy, verbose).flatMap { curlCommand =>
              logEffect(curlCommand) *>
                oldDriver.request(version, method, url, headers, body, sslConfig, proxy)
            }

          override def socket[Env1 <: Env](version: Version, url: URL, headers: Headers, app: WebSocketApp[Env1])(
            implicit
            trace: Trace,
            ev: ReqEnv =:= Scope,
          ): ZIO[Env1 & ReqEnv, Throwable, Response] =
            client.driver.socket(version, url, headers, app)
        }

        client.transform(client.bodyEncoder, client.bodyDecoder, newDriver)
      }
    }

  /**
   * Formats requests as curl commands, for `ZClientAspect.curlLogger`.
   */
  object CurlLogger {

    /**
     * Formats a request, given by its parts, as a curl command with one option per line. The body is included only when it is complete (`Body#isComplete`) and not empty. With a proxy, the proxy option ends with the text `None` when the proxy has no credentials, and with the `--proxy-user` option wrapped in `Some(...)` when it has some; the password is written as the `toString` of its `zio.Config.Secret`.
     */
    def formatCurlCommand(
      version: Version,
      method: Method,
      url: URL,
      headers: Headers,
      body: Body,
      proxy: Option[Proxy],
      verbose: Boolean,
    ): Task[String] = {
      val versionOpt = version match {
        case Version.Default  => Chunk.empty
        case Version.Http_1_0 => Chunk("--http1.0")
        case Version.Http_1_1 => Chunk("--http1.1")
      }
      val verboseOpt = if (verbose) Chunk("--verbose") else Chunk.empty
      val requestOpt = Chunk(s"--request ${method.name}")
      val headerOpt  = Chunk.fromIterable(headers.map(h => s"--header '${h.headerName}:${h.renderedValue}'"))
      val bodyOpt    = if (body.isComplete) {
        body match {
          case Body.empty => ZIO.succeed(Chunk.empty[String])
          case body       => {
            body.asString.map { bodyAsString =>
              Chunk(s"--data '${bodyAsString.replace("'", "'\\''")}'")
            }
          }
        }
      } else {
        ZIO.succeed(Chunk.empty[String])
      }
      val proxyOpt   = proxy match {
        case Some(proxy) =>
          Chunk(
            s"--proxy '${proxy.url}'" +
              proxy.credentials.map(c => s" --proxy-user  '${c.uname}:${c.upassword}'") +
              proxy.headers.map(h => s" --proxy-header '${h.headerName}:${h.renderedValue}'").mkString(" "),
          )
        case None        => Chunk.empty
      }
      bodyOpt.map { bodyAsString =>
        (
          Chunk("curl") ++
            verboseOpt ++
            requestOpt ++
            headerOpt ++
            versionOpt ++
            proxyOpt ++
            bodyAsString ++
            Chunk.single("'" + url.encode + "'")
        ).mkString(" \\\n  ")
      }
    }

    /**
     * Formats `request` as a curl command with one option per line, like the
     * other `formatCurlCommand`.
     */
    def formatCurlCommand(request: Request, verbose: Boolean = true, proxy: Option[Proxy] = None): Task[String] = {
      formatCurlCommand(
        request.version,
        request.method,
        request.url,
        request.headers,
        request.body,
        proxy,
        verbose,
      )
    }
  }
}
