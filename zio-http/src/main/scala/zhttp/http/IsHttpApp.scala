package zhttp.http

import scala.annotation.implicitNotFound

@implicitNotFound("This operation is allowed only on an Http which accepts a `Request` and produces a `Response`.")
sealed trait IsHttpApp[+R, -E, +A, -B] extends IsHttpApp.IsResponse[R, E, B] with IsHttpApp.IsRequest[A]

object IsHttpApp {
  implicit def instance[R, E]: IsHttpApp[R, E, Request, Response[R, E]] = null

  @implicitNotFound("The output of the Http should be a `Response`")
  sealed trait IsResponse[+R, -E, -B]

  @implicitNotFound("The input of the Http should be a `Request`")
  sealed trait IsRequest[+A]
}
