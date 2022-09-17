package zio.http.api

private[api] trait HeaderInputs {
  def header[A](name: String, value: TextCodec[A]): In[A] =
    In.Header(name, value)
}
