package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
private[api] trait QueryInputs {

  def query(name: String): In[String] =
    In.Query(name, TextCodec.string)

  def queryBool(name: String): In[Boolean] =
    In.Query(name, TextCodec.boolean)

  def queryInt(name: String): In[Int] =
    In.Query(name, TextCodec.int)

  def queryAs[A](name: String)(implicit codec: TextCodec[A]): In[A] =
    In.Query(name, codec)

}
