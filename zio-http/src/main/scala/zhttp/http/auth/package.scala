package zhttp.http

package object auth extends AuthedRequestSyntax {
  import Partials._

  type HttpAuthenticationMiddleware[-R, +A] =
    Http[R, HttpError, Request, AuthedRequest[A]]

  object HttpAuthenticationMiddleware {

    def ofM[A]: HttpAuthenticationMiddlewareMPartiallyApplied[A] =
      new HttpAuthenticationMiddlewareMPartiallyApplied[A]

    def of[A]: HttpAuthenticationMiddlewarePartiallyApplied[A] =
      new HttpAuthenticationMiddlewarePartiallyApplied[A]
  }

  implicit class HttpObjOps(private val obj: Http.type) {

    def collectAuthed[A]: CollectAuthedPartiallyApplied[A] =
      new CollectAuthedPartiallyApplied[A]

    def collectAuthedM[A]: CollectAuthedMPartiallyApplied[A] =
      new CollectAuthedMPartiallyApplied[A]
  }
}
