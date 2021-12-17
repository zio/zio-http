package zhttp.http

import scala.annotation.implicitNotFound
import scala.language.implicitConversions

@implicitNotFound(
  "This operation is only allowed if the output is a type of Response. " +
    "However your Http instance produces the type ${B}, on which this operation can not be applied.",
)
sealed trait IsResponse[-R, +E, -B] extends scala.Conversion[B, Response[R, E]] with Serializable  {
  override def apply(B: B): Response[R, E] = B.asInstanceOf[Response[R, E]]
}

object IsResponse {
  implicit def isResponse[R, E, B](implicit ev: B <:< Response[R, E]): IsResponse[R, E, B] =
    new IsResponse[R, E, B] {}
}
