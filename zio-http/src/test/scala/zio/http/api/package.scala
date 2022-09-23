package zio.http

import scala.language.implicitConversions

package object api {
  implicit def stringToIn(s: String): In[Unit] = In.literal(s)
}
