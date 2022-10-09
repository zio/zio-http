package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
private[api] trait QueryInputs {

  def query(name: String): In[In.QueryType, String] =
    In.Query(name, TextCodec.string)

  def queryBool(name: String): In[In.QueryType, Boolean] =
    In.Query(name, TextCodec.boolean)

  def queryInt(name: String): In[In.QueryType, Int] =
    In.Query(name, TextCodec.int)

  def queryAs[A](name: String)(implicit codec: TextCodec[A]): In[In.QueryType, A] =
    In.Query(name, codec)

}
