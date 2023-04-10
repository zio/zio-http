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

import zio.{NonEmptyChunk, Trace}

import zio.http.Header.AccessControlAllowHeaders
import zio.http._
import zio.http.middleware.Cors.CorsConfig

private[zio] trait Cors {

  /**
   * Creates a middleware for Cross-Origin Resource Sharing (CORS).
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  final def cors(config: CorsConfig = CorsConfig()): HttpAppMiddleware[Nothing, Any, Nothing, Any] = {
    def allowedHeaders(
      requestedHeaders: Option[Header.AccessControlRequestHeaders],
      allowedHeaders: Header.AccessControlAllowHeaders,
    ): Header.AccessControlAllowHeaders =
      // Returning an intersection of requested headers and allowed headers
      // if there are no requested headers, we return the configured allowed headers without modification
      allowedHeaders match {
        case AccessControlAllowHeaders.Some(values) =>
          requestedHeaders match {
            case Some(Header.AccessControlRequestHeaders(headers)) =>
              val intersection = headers.toSet.intersect(values.toSet)
              NonEmptyChunk.fromIterableOption(intersection) match {
                case Some(values) => AccessControlAllowHeaders.Some(values)
                case None         => AccessControlAllowHeaders.None
              }
            case None                                              => allowedHeaders
          }
        case AccessControlAllowHeaders.All          =>
          requestedHeaders match {
            case Some(Header.AccessControlRequestHeaders(headers)) => AccessControlAllowHeaders.Some(headers)
            case _                                                 => AccessControlAllowHeaders.All
          }
        case AccessControlAllowHeaders.None         => AccessControlAllowHeaders.None
      }

    def corsHeaders(
      allowOrigin: Header.AccessControlAllowOrigin,
      requestedHeaders: Option[Header.AccessControlRequestHeaders],
      isPreflight: Boolean,
    ): Headers =
      Headers(
        allowOrigin,
        config.allowedMethods,
        config.allowCredentials,
      ) ++
        Headers.ifThenElse(isPreflight)(
          onTrue = Headers(allowedHeaders(requestedHeaders, config.allowedHeaders)),
          onFalse = Headers(config.exposedHeaders),
        ) ++ config.maxAge.fold(Headers.empty)(Headers(_))

    new HttpAppMiddleware.Simple[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        http: Http[R1, Err1, Request, Response],
      )(implicit trace: Trace): Http[R1, Err1, Request, Response] =
        Http.fromHttp[Request] { request =>
          val originHeader = request.headers.header(Header.Origin)
          val acrmHeader   = request.headers.header(Header.AccessControlRequestMethod)
          val acrhHeader   = request.header(Header.AccessControlRequestHeaders)

          (
            request.method,
            originHeader,
            acrmHeader,
          ) match {
            case (Method.OPTIONS, Some(origin), Some(acrm)) =>
              config.allowedOrigin(origin) match {
                case Some(allowOrigin) if config.allowedMethods.contains(acrm.method) =>
                  Handler
                    .response(
                      Response(
                        Status.NoContent,
                        headers = corsHeaders(allowOrigin, acrhHeader, isPreflight = true),
                      ),
                    )
                    .toHttp
                case _                                                                =>
                  http
              }
            case (_, Some(origin), _)                       =>
              config.allowedOrigin(origin) match {
                case Some(allowOrigin) if config.allowedMethods.contains(request.method) =>
                  http @@ HttpAppMiddleware.addHeaders(corsHeaders(allowOrigin, acrhHeader, isPreflight = false))
                case _                                                                   =>
                  http
              }
            case _                                          =>
              http
          }
        }
    }
  }
}

object Cors {
  final case class CorsConfig(
    allowedOrigin: Header.Origin => Option[Header.AccessControlAllowOrigin] = origin =>
      Some(Header.AccessControlAllowOrigin.Specific(origin)),
    allowedMethods: Header.AccessControlAllowMethods = Header.AccessControlAllowMethods.All,
    allowedHeaders: Header.AccessControlAllowHeaders = Header.AccessControlAllowHeaders.All,
    allowCredentials: Header.AccessControlAllowCredentials = Header.AccessControlAllowCredentials.Allow,
    exposedHeaders: Header.AccessControlExposeHeaders = Header.AccessControlExposeHeaders.All,
    maxAge: Option[Header.AccessControlMaxAge] = None,
  )
}
