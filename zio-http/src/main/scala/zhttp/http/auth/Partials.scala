package zhttp.http.auth

import zhttp.http.{ Http, HttpError, Request, Response, ResponseM }
import zio.ZIO

object Partials {
  private[auth] final case class CollectAuthedPartiallyApplied[A](private val dummy: Boolean = false)
    extends AnyVal {

    def apply[R, E](
      pf: PartialFunction[AuthedRequest[A], Response[R, E]]
    ): Http[R, E, AuthedRequest[A], Response[R, E]] =
      Http.identity[AuthedRequest[A]] >>= { a =>
        if (pf.isDefinedAt(a))
          Http.succeed(pf(a))
        else
          Http.empty
      }
  }

  private[auth] final case class CollectAuthedMPartiallyApplied[A](private val dummy: Boolean = false)
    extends AnyVal {

    def apply[R, E](
      pf: PartialFunction[AuthedRequest[A], ResponseM[R, E]]
    ): Http[R, E, AuthedRequest[A], Response[R, E]] =
      Http.identity[AuthedRequest[A]] >>= { a =>
        if (pf.isDefinedAt(a))
          Http.fromEffect(pf(a))
        else
          Http.empty
      }
  }

  private[auth] final case class HttpAuthenticationMiddlewarePartiallyApplied[A](private val dummy: Boolean = false)
    extends AnyVal {

    def apply[R, E <: HttpError, A1 >: A](
      f: Request => Either[E, AuthedRequest[A1]]
    ): HttpAuthenticationMiddleware[R, A1] = {
      Http.identity[Request] >>= { request =>
        f(request).fold(Http.fail, Http.succeed)
      }
    }
  }

  private[auth] final case class HttpAuthenticationMiddlewareMPartiallyApplied[A](private val dummy: Boolean = false)
    extends AnyVal {

    def apply[R, E <: HttpError, A1 >: A](
      f: Request => ZIO[R, E, AuthedRequest[A1]]
    ): HttpAuthenticationMiddleware[R, A1] =
      Http.fromEffectFunction(f)
  }
}
