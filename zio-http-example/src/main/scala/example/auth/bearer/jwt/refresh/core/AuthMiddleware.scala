package example.auth.bearer.jwt.refresh.core

import zio._

import zio.http._

object AuthMiddleware {
  def jwtAuth(realm: String): HandlerAspect[JwtTokenService, UserInfo] =
    HandlerAspect.interceptIncomingHandler {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Bearer(token)) =>
            ZIO
              .serviceWithZIO[JwtTokenService](_.verifyAccessToken(token.value.asString))
              .map(userInfo => (request, userInfo))
              .orElseFail(
                Response
                  .unauthorized("Invalid or expired token.")
                  .addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))),
              )
          case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))))
        }
      }
    }
}