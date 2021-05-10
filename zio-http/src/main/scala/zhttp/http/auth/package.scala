package zhttp.http

package object auth extends AuthedRequestSyntax with AuthMiddlewareSyntax {
  import Partials._

  type Auth[-R, +E, +A] =
    Http[R, E, Request, AuthedRequest[A]]

  object Auth {

    def customM[A]: AuthMPartiallyApplied[A] =
      new AuthMPartiallyApplied[A]

    def custom[A]: AuthPartiallyApplied[A] =
      new AuthPartiallyApplied[A]
  }
}
