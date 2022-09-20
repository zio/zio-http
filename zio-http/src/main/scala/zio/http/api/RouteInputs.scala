package zio.http.api

import java.util.UUID
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
private[api] trait RouteInputs {
  def literal(string: String): In[Unit] =
    In.Route(TextCodec.constant(string))

  val int: In[Int] =
    In.Route(TextCodec.int)

  val string: In[String] =
    In.Route(TextCodec.string)

  val uuid: In[UUID] =
    In.Route(TextCodec.uuid)
}
