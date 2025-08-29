package example.auth.bearer.jwt.symmetric.core

import zio._
import zio.http._

object AuthMiddleware {
  def jwtAuth(realm: String): HandlerAspect[JwtTokenServiceClaim, UserInfo] =
    HandlerAspect.interceptIncomingHandler {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Bearer(token)) =>
            ZIO
              .serviceWithZIO[JwtTokenServiceClaim](_.verify(token.value.asString))
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

  def jwtAuthentication(realm: String): HandlerAspect[JwtTokenService & UserService, UserInfo] =
    HandlerAspect.interceptIncomingHandler {
      handler { (request: Request) =>
        request.header(Header.Authorization) match {
          case Some(Header.Authorization.Bearer(token)) =>
            ZIO
              .serviceWithZIO[JwtTokenService](_.verify(token.value.asString))
              .flatMap { username =>
                ZIO.serviceWithZIO[UserService](_.getUser(username))
              }
              .map(u => UserInfo(u.username, u.email, u.roles))
              .map(userInfo => (request, userInfo))
              .orElseFail(
                Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))),
              )
          case _ => ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm))))
        }
      }
    }
}
