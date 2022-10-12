package zio.http

import scala.language.implicitConversions

package object api {
  implicit def stringToIn(s: String): In[In.RouteType, Unit] = In.literal(s)
}
