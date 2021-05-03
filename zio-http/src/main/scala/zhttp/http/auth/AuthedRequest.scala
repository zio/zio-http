package zhttp.http.auth

import zhttp.http.Request

final case class AuthedRequest[+A](authInfo: A, request: Request)
