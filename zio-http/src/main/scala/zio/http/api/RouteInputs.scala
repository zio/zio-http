package zio.http.api

private[api] trait RouteInputs {
  def literal(string: String): In[Unit] =
    In.Route(TextCodec.constant(string))

  val int: In[Int] =
    In.Route(TextCodec.int)
}
