package zhttp.api

sealed trait HttpMethod extends Product with Serializable { self =>
  def toZioHttpMethod: zhttp.http.Method =
    self match {
      case HttpMethod.GET    => zhttp.http.Method.GET
      case HttpMethod.POST   => zhttp.http.Method.POST
      case HttpMethod.PATCH  => zhttp.http.Method.PATCH
      case HttpMethod.PUT    => zhttp.http.Method.PUT
      case HttpMethod.DELETE => zhttp.http.Method.DELETE
    }
}

object HttpMethod {
  case object GET    extends HttpMethod
  case object POST   extends HttpMethod
  case object PATCH  extends HttpMethod
  case object PUT    extends HttpMethod
  case object DELETE extends HttpMethod
}
