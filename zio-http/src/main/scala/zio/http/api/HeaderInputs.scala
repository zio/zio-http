package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace

private[api] trait HeaderInputs {
  def header[A](name: String, value: TextCodec[A]): In[A] =
    In.Header(name, value)
}
