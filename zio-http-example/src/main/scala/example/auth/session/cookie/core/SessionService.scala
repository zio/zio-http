package example.auth.session.cookie.core

import zio._

class SessionService private(private val sessionStore: Ref[Map[String, String]]) {

  // Session cookie name
  val SESSION_COOKIE_NAME = "session_id"

  // Session timeout in seconds
//  val SESSION_TIMEOUT = 300

  private def generateSessionId(): UIO[String] =
    ZIO.randomWith(_.nextUUID).map(_.toString)

  def createSession(username: String): UIO[String] = {
    for {
      sessionId <- generateSessionId()
      _         <- sessionStore.update(_ + (sessionId -> username))
    } yield sessionId
  }

  def getSession(sessionId: String): UIO[Option[String]] =
    sessionStore.get.map(_.get(sessionId))

  def removeSession(sessionId: String): UIO[Unit] =
    sessionStore.update(_ - sessionId)

}

object SessionService {
  def live : ZLayer[Any, Nothing, SessionService] =
    ZLayer.fromZIO(make)

  def make: UIO[SessionService] =
    Ref.make(Map.empty[String, String]).map(new SessionService(_))
}
