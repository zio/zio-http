package zhttp.http

sealed trait IsHttpApp[+R, -E, +A, -B] extends IsHttpApp.IsResponse[R, E, B] with IsHttpApp.IsRequest[A]

object IsHttpApp {
  implicit def instance[R, E]: IsHttpApp[R, E, Request, Response[R, E]] = null

  sealed trait IsResponse[+R, -E, -B]
  sealed trait IsRequest[+A]
}
