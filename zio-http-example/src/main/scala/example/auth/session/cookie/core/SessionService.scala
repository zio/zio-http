package example.auth.session.cookie.core

import zio._

class SessionService private (private val store: Ref[Map[String, String]]) {
  private def generateSessionId(): UIO[String] =
    ZIO.randomWith(_.nextUUID).map(_.toString)

  def create(username: String): UIO[String] =
    for {
      sessionId <- generateSessionId()
      _         <- store.update(_ + (sessionId -> username))
    } yield sessionId

  def get(sessionId: String): UIO[Option[String]] =
    store.get.map(_.get(sessionId))

  def remove(sessionId: String): UIO[Unit] =
    store.update(_ - sessionId)
}

object SessionService {
  def live: ZLayer[Any, Nothing, SessionService] =
    ZLayer.fromZIO {
      Ref.make(Map.empty[String, String]).map(new SessionService(_))
    }
}
