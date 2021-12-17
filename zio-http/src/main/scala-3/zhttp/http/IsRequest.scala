package zhttp.http

import scala.annotation.implicitNotFound

@implicitNotFound(
  "This operation is only allowed if the input is a type of Request. " +
    "However your Http instance accepts the type ${A}, on which this operation can not be applied.",
)
sealed trait IsRequest[+A] extends scala.Conversion[Request, A] with Serializable {
  override def apply(request: Request): A = request.asInstanceOf[A]
}

object IsRequest extends IsRequest[Request] {
  implicit val isRequest: IsRequest[Request] = IsRequest
}
