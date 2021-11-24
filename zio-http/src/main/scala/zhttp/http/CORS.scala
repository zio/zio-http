package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames

final case class CORSConfig(
  anyOrigin: Boolean = false,
  anyMethod: Boolean = true,
  allowCredentials: Boolean = false,
  allowedOrigins: String => Boolean = _ => false,
  allowedMethods: Option[Set[Method]] = None,
  allowedHeaders: Option[Set[String]] = Some(Set("Content-Type", "Authorization", "*")),
  exposedHeaders: Option[Set[String]] = Some(Set("*")),
)

object CORS {
  def DefaultCORSConfig =
    CORSConfig(anyOrigin = true, allowCredentials = true)

  def apply[R, E](httpApp: HttpApp[R, E], config: CORSConfig = DefaultCORSConfig): HttpApp[R, E] = {
    def allowCORS(origin: Header, acrm: Method): Boolean =
      (config.anyOrigin, config.anyMethod, origin.value.toString(), acrm) match {
        case (true, true, _, _)           => true
        case (true, false, _, acrm)       =>
          config.allowedMethods.exists(_.contains(acrm))
        case (false, true, origin, _)     => config.allowedOrigins(origin)
        case (false, false, origin, acrm) =>
          config.allowedMethods.exists(_.contains(acrm)) &&
            config.allowedOrigins(origin)
      }

    def corsHeaders(origin: Header, method: Method, isPreflight: Boolean = false): List[Header] = {
      (method match {
        case _ if isPreflight =>
          config.allowedHeaders.fold(List.empty[Header])(h => {
            List(
              Header.custom(
                HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(),
                h.mkString(","),
              ),
            )
          })
        case _                =>
          config.exposedHeaders.fold(List.empty[Header])(h => {
            List(
              Header.custom(
                HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(),
                h.mkString(","),
              ),
            )
          })
      }) ++
        List(
          Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), origin.value),
          Header.custom(
            HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(),
            config.allowedMethods.fold(method.toString())(m => m.map(m => m.toString()).mkString(",")),
          ),
        ) ++
        (if (config.allowCredentials)
           List(
             Header
               .custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), config.allowCredentials.toString()),
           )
         else List.empty[Header])
    }

    Http.flatten {
      Http.fromFunction[Request](req => {
        (
          req.method,
          req.getHeader(HttpHeaderNames.ORIGIN),
          req.getHeader(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD),
        ) match {
          case (Method.OPTIONS, Some(origin), Some(acrm))
              if allowCORS(origin, Method.fromString(acrm.value.toString())) =>
            Http.succeed(
              Response(
                Status.NO_CONTENT,
                headers = corsHeaders(origin, Method.fromString(acrm.value.toString()), isPreflight = true),
              ),
            )
          case (_, Some(origin), _) if allowCORS(origin, req.method) =>
            httpApp >>>
              Http.fromFunction[Response[R, E]] {
                case r: Response[R, E] =>
                  r.copy(headers = r.headers ++ corsHeaders(origin, req.method))
                case x                 =>
                  x
              }
          case _                                                     => httpApp
        }
      })
    }
  }
}
