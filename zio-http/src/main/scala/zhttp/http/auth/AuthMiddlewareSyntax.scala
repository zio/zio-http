package zhttp.http.auth

import zhttp.http.{Http, Request}

private[auth] trait AuthMiddlewareSyntax {
  implicit class AuthOps[R, E, A](private val authMiddleware: Auth[R, E, A]) {
    def apply[R1 <: R, E1 >: E, B](httpApp: Http[R1, E1, AuthedRequest[A], B]): Http[R1, E1, Request, B] =
      authMiddleware >>> httpApp
  }
}

// R = Console
// initial R1 = Logging
// => R1 = Console with Logging

// E = HttpError
// inital E1 = Nothing
// => HttpError

// E = Nothing
// initial E1 = Throwable
// => Throwable
