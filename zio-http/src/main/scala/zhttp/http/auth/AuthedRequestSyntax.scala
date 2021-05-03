package zhttp.http.auth

import zhttp.http.Request

trait AuthedRequestSyntax {
  object `@` {
    def unapply[A](authedRequest: AuthedRequest[A]): Option[(A, Request)] =
      Option(authedRequest.authInfo -> authedRequest.request)
  }
}
