package zio.http.api

private[api] trait QueryInputs {

  def query(name: String): In[String] =
    In.Query(name, TextCodec.string)

}
