package zio.http.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import zio.{Trace, Unsafe}
import zio.http._
import zio.http.middleware.Cors.{CorsConfig, buildHeaders}
import zio.http.model._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] trait Cors {

  /**
   * Creates a middleware for Cross-Origin Resource Sharing (CORS).
   * @see
   *   https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
   */
  final def cors(config: CorsConfig = CorsConfig()): HttpAppMiddleware[Any, Nothing] = {
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
        onTrue = buildHeaders(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(), config.allowedHeaders),
        onFalse = buildHeaders(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), config.exposedHeaders),
      ) ++
        Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), origin._2) ++
        buildHeaders(
          HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(),
          config.allowedMethods.map(_.map(_.toJava.name())),
        ) ++
        Headers.when(config.allowCredentials) {
          Headers(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS, config.allowCredentials.toString)
        }
    }

    new HttpAppMiddleware[Any, Nothing] {
      override def apply[R1 <: Any, Err1 >: Nothing](
        http: Http[R1, Err1, Request, Response],
      )(implicit trace: Trace): Http[R1, Err1, Request, Response] =
        Http.fromHttp[Request] { request =>
          (
            request.method,
            request.headers.header(HttpHeaderNames.ORIGIN),
            request.headers.header(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD),
          ) match {
            case (Method.OPTIONS, Some(origin), Some(acrm))
                if allowCORS(origin, Method.fromString(acrm.value.toString)) =>
              Handler
                .response(
                  Response(
                    Status.NoContent,
                    headers = corsHeaders(origin, Method.fromString(acrm._2.toString), isPreflight = true),
                  ),
                )
                .toHttp
            case (_, Some(origin), _) if allowCORS(origin, request.method) =>
              http @@ Middleware.addHeaders(corsHeaders(origin, request.method, isPreflight = false))
            case _                                                         =>
              http
          }
        }
    }
  }
}

object Cors {
  final case class CorsConfig(
    anyOrigin: Boolean = true,
    anyMethod: Boolean = true,
    allowCredentials: Boolean = true,
    allowedOrigins: String => Boolean = _ => false,
    allowedMethods: Option[Set[Method]] = None,
    allowedHeaders: Option[Set[String]] = Some(
      Set(HttpHeaderNames.CONTENT_TYPE.toString, HttpHeaderNames.AUTHORIZATION.toString, "*"),
    ),
    exposedHeaders: Option[Set[String]] = Some(Set("*")),
  )

  private def buildHeaders(headerName: String, values: Option[Set[String]]): Headers = {
    values match {
      case Some(headerValues) =>
        Headers(headerValues.toList.map(value => Header(headerName, value)))
      case None               => Headers.empty
    }
  }
}
