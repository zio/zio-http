package zio.http.api

private[api] trait RouteInputs {
  def literal(string: String): In[Unit] =
    In.Route(TextCodec.constant(string))

  lazy val int: In[Int] =
    In.Route(TextCodec.int)

  lazy val string: In[String] =
    In.Route(TextCodec.string)
}
