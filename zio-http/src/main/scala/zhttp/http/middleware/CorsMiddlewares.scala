package zhttp.http.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.http.middleware.CORS.DefaultCORSConfig

private[zhttp] trait CorsMiddlewares {

  /**
   * Creates a middleware for Cross-Origin Resource Sharing (CORS).
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  def cors[R, E](config: CorsConfig = DefaultCORSConfig): HttpMiddleware[R, E] = {
    def allowCORS(origin: Header, acrm: Method): Boolean                           =
      (config.anyOrigin, config.anyMethod, origin._2.toString, acrm) match {
        case (true, true, _, _)           => true
        case (true, false, _, acrm)       =>
          config.allowedMethods.exists(_.contains(acrm))
        case (false, true, origin, _)     => config.allowedOrigins(origin)
        case (false, false, origin, acrm) =>
          config.allowedMethods.exists(_.contains(acrm)) &&
          config.allowedOrigins(origin)
      }
    def corsHeaders(origin: Header, method: Method, isPreflight: Boolean): Headers = {
      Headers.ifThenElse(isPreflight)(
        onTrue = config.allowedHeaders.fold(Headers.empty) { h =>
          Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(), h.mkString(","))
        },
        onFalse = config.exposedHeaders.fold(Headers.empty) { h =>
          Headers(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), h.mkString(","))
        },
      ) ++
        Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), origin._2) ++
        Headers(
          HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(),
          config.allowedMethods.fold(method.toString())(m => m.map(m => m.toString()).mkString(",")),
        ) ++
        Headers.when(config.allowCredentials) {
          Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, config.allowCredentials.toString)
        }
    }
    Middleware.fromMiddlewareFunction((method, _, headers) => {
      (
        method,
        headers.getHeader(HttpHeaderNames.ORIGIN),
        headers.getHeader(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD),
      ) match {
        case (Method.OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, Method.fromString(acrm._2.toString)) =>
          Middleware.succeed(
            Response(
              Status.NO_CONTENT,
              headers = corsHeaders(origin, Method.fromString(acrm._2.toString), isPreflight = true),
            ),
          )
        case (_, Some(origin), _) if allowCORS(origin, method)                                                    =>
          Middleware.addHeader(corsHeaders(origin, method, isPreflight = false))
        case _ => Middleware.identity
      }
    })
  }
}
