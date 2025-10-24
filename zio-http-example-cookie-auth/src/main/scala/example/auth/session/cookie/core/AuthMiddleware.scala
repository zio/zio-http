package example.auth.session.cookie.core

import zio._

import zio.http._

object AuthMiddleware {
  def cookieAuth(cookieName: String = "session_id"): HandlerAspect[SessionService & UserService, User] =
    HandlerAspect.interceptIncomingHandler {
      Handler.fromFunctionZIO[Request] { request =>
        ZIO.serviceWithZIO[SessionService] { sessionService =>
          request.cookie(cookieName) match {
            case Some(cookie) =>
              sessionService.get(cookie.content).flatMap {
                case Some(username) =>
                  ZIO
                    .serviceWithZIO[UserService](_.getUser(username))
                    .map(u => (request, u))
                    .orElseFail(
                      Response.unauthorized(s"User not found!"),
                    )
                case None           =>
                  ZIO.fail(Response.unauthorized("Invalid or expired session!"))
              }
            case None         =>
              ZIO.fail(Response.unauthorized("No session cookie found!"))
          }
        }
      }
    }
}
