package zhttp.http.auth

import zhttp.http.{Http, Request}
import zio.ZIO

object Partials {
  private[auth] final case class AuthMPartiallyApplied[A](private val dummy: Boolean = false) extends AnyVal {

    def apply[R, E, A1 >: A](
      f: Request => ZIO[R, E, A1],
    ): Auth[R, E, A1] =
      Http.fromEffectFunction(request => f(request).map(AuthedRequest(_, request)))
  }

  private[auth] final case class AuthPartiallyApplied[A](private val dummy: Boolean = false) extends AnyVal {

    def apply[R, E, A1 >: A](
      f: Request => Either[E, A1],
    ): Auth[R, E, A1] = {
      Http.identity[Request] >>= { request =>
        f(request).fold(Http.fail, auth => Http.succeed(AuthedRequest(auth, request)))
      }
    }
  }
}
