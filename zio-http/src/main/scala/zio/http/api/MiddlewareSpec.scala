package zio.http.api

final case class MiddlewareSpec[MiddlewareOut](
  // middlewareIn: In[MiddlewareIn], // TODO; will be added later
  middlewareOut: Out[MiddlewareOut]
)

object MiddlewareSpec {
  def empty: MiddlewareSpec[Unit] =
    MiddlewareSpec(Out.unit)

  def addHeader(key: String, value: String): MiddlewareSpec[Unit] =
    MiddlewareSpec(Out.AddHeader(key, value))
}
