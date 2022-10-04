package zio.http.api

import zio.http.{Middleware}

final case class MiddlewareSpec[MiddlewareOut](
  // middlewareIn: In[MiddlewareIn],
  middlewareOut: In[Unit],
) {
  def toMiddleware =
    MiddlewareSpec.toMiddleware(middlewareOut)
}

object MiddlewareSpec {
  def addHeader(key: String, value: String): MiddlewareSpec[Unit] =
    MiddlewareSpec(In.header(key, TextCodec.constant(value)))


  private[api] def toMiddleware(in: In[Unit]) =
    in match {
      case atom: In.Atom[_] => atom match {
        case In.BasicAuthenticate(a, b) => Middleware.basicAuth(a, b)
        case In.Header(name, value) => value match {
          case TextCodec.Constant(value) => Middleware.addHeader(name, value)
          case _ => Middleware.empty
        }
        case _ => Middleware.empty
      }
      case _ => Middleware.empty
    }
}
