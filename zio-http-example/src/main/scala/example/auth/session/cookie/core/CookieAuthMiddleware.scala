package example.auth.session.cookie.core

import zio._
import zio.http._

object CookieAuthMiddleware {
  val SESSION_COOKIE_NAME = "session_id"

  val cookieAuth: HandlerAspect[SessionService, String] =
    HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
      ZIO.serviceWithZIO[SessionService] { sessionService =>
        request.cookie(SESSION_COOKIE_NAME) match {
          case Some(cookie) =>
            sessionService.getSession(cookie.content).flatMap {
              case Some(username) =>
                ZIO.succeed((request, username))
              case None           =>
                ZIO.fail(Response.unauthorized("Invalid or expired session!"))
            }
          case None         =>
            ZIO.fail(Response.unauthorized("No session cookie found!"))
        }
      }
    })
}
