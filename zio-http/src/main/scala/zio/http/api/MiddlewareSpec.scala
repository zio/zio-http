package zio.http.api

import zio.http.model.Headers
import zio.http.model.Headers.Header

final case class MiddlewareSpec[MiddlewareOut](
  // middlewareIn: In[MiddlewareIn], // TODO; will be added later
  middlewareOut: Out[MiddlewareOut]
)

object MiddlewareSpec {
  def addHeader[A](key: String, value: String): MiddlewareSpec[Unit] =
    MiddlewareSpec(Out.AddHeader(key, value))
}
