package example.auth.bearer.opaque.core

import zio._
import zio.http._

object AuthHandlerAspect {
  def authenticate: HandlerAspect[TokenService with UserService, User] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      request.header(Header.Authorization) match {
        case Some(Header.Authorization.Bearer(token)) =>
          ZIO.serviceWithZIO[TokenService](_.validate(token.stringValue)).flatMap {
            case Some(username) =>
              ZIO
                .serviceWithZIO[UserService](_.getUser(username))
                .map(user => (request, user))
                .orElse(
                  ZIO.fail(
                    Response.unauthorized("User not found!"),
                  ),
                )
            case None           =>
              ZIO.fail(Response.unauthorized("Invalid or expired token!"))
          }
        case _                                        =>
          ZIO.fail(
            Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))),
          )
      }
    })
}
