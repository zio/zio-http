/*
 * Copyright 2023 the ZIO HTTP contributors.
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

import zio._

trait Middleware[-UpperEnv] { self =>
  def apply[Env1 <: UpperEnv, Err](
    routes: Routes[Env1, Err],
  ): Routes[Env1, Err]

  def @@[UpperEnv1 <: UpperEnv](
    that: Middleware[UpperEnv1],
  ): Middleware[UpperEnv1] =
    self ++ that

  def ++[UpperEnv1 <: UpperEnv](
    that: Middleware[UpperEnv1],
  ): Middleware[UpperEnv1] =
    new Middleware[UpperEnv1] {
      def apply[Env1 <: UpperEnv1, Err](
        routes: Routes[Env1, Err],
      ): Routes[Env1, Err] =
        self(that(routes))
    }
}
object Middleware extends HandlerAspects {

  /**
   * Configuration for the CORS aspect.
   */
  final case class CorsConfig(
    allowedOrigin: Header.Origin => Option[Header.AccessControlAllowOrigin] = origin =>
      Some(Header.AccessControlAllowOrigin.Specific(origin)),
    allowedMethods: Header.AccessControlAllowMethods = Header.AccessControlAllowMethods.All,
    allowedHeaders: Header.AccessControlAllowHeaders = Header.AccessControlAllowHeaders.All,
    allowCredentials: Header.AccessControlAllowCredentials = Header.AccessControlAllowCredentials.Allow,
    exposedHeaders: Header.AccessControlExposeHeaders = Header.AccessControlExposeHeaders.All,
    maxAge: Option[Header.AccessControlMaxAge] = None,
  )

  /**
   * Creates a aspect for Cross-Origin Resource Sharing (CORS) using default
   * options.
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  def cors: Middleware[Any] = cors(CorsConfig())

  /**
   * Creates a aspect for Cross-Origin Resource Sharing (CORS).
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  def cors(config: CorsConfig): Middleware[Any] = {
    def allowedHeaders(
      requestedHeaders: Option[Header.AccessControlRequestHeaders],
      allowedHeaders: Header.AccessControlAllowHeaders,
    ): Header.AccessControlAllowHeaders =
      // Returning an intersection of requested headers and allowed headers
      // if there are no requested headers, we return the configured allowed headers without modification
      allowedHeaders match {
        case Header.AccessControlAllowHeaders.Some(values) =>
          requestedHeaders match {
            case Some(Header.AccessControlRequestHeaders(headers)) =>
              val intersection = headers.toSet.intersect(values.toSet)
              NonEmptyChunk.fromIterableOption(intersection) match {
                case Some(values) => Header.AccessControlAllowHeaders.Some(values)
                case None         => Header.AccessControlAllowHeaders.None
              }
            case None                                              => allowedHeaders
          }
        case Header.AccessControlAllowHeaders.All          =>
          requestedHeaders match {
            case Some(Header.AccessControlRequestHeaders(headers)) => Header.AccessControlAllowHeaders.Some(headers)
            case _                                                 => Header.AccessControlAllowHeaders.All
          }
        case Header.AccessControlAllowHeaders.None         => Header.AccessControlAllowHeaders.None
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

    // HandlerAspect:
    val aspect =
      HandlerAspect.interceptHandlerStateful[Any, Headers, Unit](
        Handler.fromFunction[Request] { request =>
          val originHeader = request.header(Header.Origin)
          val acrhHeader   = request.header(Header.AccessControlRequestHeaders)

          originHeader match {
            case Some(origin) =>
              config.allowedOrigin(origin) match {
                case Some(allowOrigin) if config.allowedMethods.contains(request.method) =>
                  corsHeaders(allowOrigin, acrhHeader, isPreflight = false) -> (request, ())
                case _                                                                   =>
                  Headers.empty -> (request, ())
              }

            case None => Headers.empty -> (request, ())
          }
        },
      )(Handler.fromFunction[(Headers, Response)] { case (headers, response) =>
        response.addHeaders(headers)
      })

    val optionsRoute =
      Method.OPTIONS / trailing -> handler { (_: Path, request: Request) =>
        val originHeader = request.header(Header.Origin)
        val acrmHeader   = request.header(Header.AccessControlRequestMethod)
        val acrhHeader   = request.header(Header.AccessControlRequestHeaders)

        (
          originHeader,
          acrmHeader,
        ) match {
          case (Some(origin), Some(acrm)) =>
            config.allowedOrigin(origin) match {
              case Some(allowOrigin) if config.allowedMethods.contains(acrm.method) =>
                Response(
                  Status.NoContent,
                  headers = corsHeaders(allowOrigin, acrhHeader, isPreflight = true),
                )

              case _ =>
                Response.notFound
            }
          case _                          =>
            Response.notFound
        }
      }

    new Middleware[Any] {
      def apply[Env1, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        (routes @@ aspect) :+ optionsRoute
    }
  }

  def timeout(duration: Duration): Middleware[Any] =
    new Middleware[Any] {
      def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform[Env1] { handler =>
          handler.timeoutFail(Response(status = Status.RequestTimeout))(duration)
        }
    }
}
