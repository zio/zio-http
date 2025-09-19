package example.auth.bearer.oauth.core
import zio._

import zio.http._

object AuthMiddleware {
  def bearerAuth(realm: String): HandlerAspect[JwtTokenService, UserInfo] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
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

        case _ =>
          ZIO.fail(
            Response.unauthorized
              .addHeaders(
                Headers(Header.WWWAuthenticate.Bearer(realm = "Access")),
              ),
          )
      }
    })
}
