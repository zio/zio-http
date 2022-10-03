package zio.http.api

final case class MiddlewareSpec[MiddlewareOut](
  // middlewareIn: In[MiddlewareIn], // TODO; will be added later
  spec: In[MiddlewareOut],
)

object MiddlewareSpec {
  def addHeader(key: String, value: String): MiddlewareSpec[Unit] =
    MiddlewareSpec(In.header(key, TextCodec.constant(value)))
}
