package zio.http.api

import java.util.UUID
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[api] trait RouteInputs {
  def literal(string: String): In[In.RouteType, Unit] =
    In.Route(TextCodec.constant(string))

  val int: In[In.RouteType, Int] =
    In.Route(TextCodec.int)

  val string: In[In.RouteType, String] =
    In.Route(TextCodec.string)

  val uuid: In[In.RouteType, UUID] =
    In.Route(TextCodec.uuid)
}
