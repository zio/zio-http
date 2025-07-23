package example.auth.digest.core

import zio.Config._
import zio._

case class User(username: String, password: Secret, email: String)

trait UserService {
  def getUser(username: String): Task[Option[User]]
  def addUser(user: User): Task[Unit]
  def updateEmail(username: String, newEmail: String): Task[Unit]
}

case class UserServiceLive(users: Ref[Map[String, User]]) extends UserService {
  def getUser(username: String): Task[Option[User]] =
    users.get.map(_.get(username))

  def addUser(user: User): Task[Unit] =
    users.update(_.updated(user.username, user))

  def updateEmail(username: String, newEmail: String): Task[Unit] =
    users.update { currentUsers =>
      currentUsers.get(username) match {
        case Some(user) => currentUsers.updated(username, user.copy(email = newEmail))
        case None       => currentUsers // No change if user not found
      }
    }
}

object UserService {
  private val initialUsers = Map(
    "john"  -> User("john", Secret("password123"), "john@example.com"),
    "jane"  -> User("jane", Secret("secret456"), "jane@example.com"),
    "admin" -> User("admin", Secret("admin123"), "admin@company.com"),
  )

  val live: ZLayer[Any, Nothing, UserService] =
    ZLayer.fromZIO(Ref.make(initialUsers).map(UserServiceLive(_)))
}
