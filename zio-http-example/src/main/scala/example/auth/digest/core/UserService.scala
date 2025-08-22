package example.auth.digest.core

import example.auth.digest.core.UserServiceError._
import zio.Config._
import zio._

case class User(username: String, password: Secret, email: String)

sealed trait UserServiceError
object UserServiceError {
  case class UserNotFound(username: String)      extends UserServiceError
  case class UserAlreadyExists(username: String) extends UserServiceError
}

trait UserService {
  def getUser(username: String): IO[UserServiceError, User]
  def addUser(user: User): IO[UserServiceError, Unit]
  def updateEmail(username: String, newEmail: String): IO[UserServiceError, Unit]
}

case class UserServiceLive(users: Ref[Map[String, User]]) extends UserService {

  def getUser(username: String): IO[UserServiceError, User] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.get(username)).orElseFail(UserNotFound(username))
    }

  def addUser(user: User): IO[UserServiceError, Unit] =
    users.get.flatMap { userMap =>
      ZIO.when(userMap.contains(user.username)) {
        ZIO.fail(UserAlreadyExists(user.username))
      } *> users.update(_.updated(user.username, user))
    }

  def updateEmail(username: String, newEmail: String): IO[UserServiceError, Unit] = for {
    currentUsers <- users.get
    user         <- ZIO.fromOption(currentUsers.get(username)).orElseFail(UserNotFound(username))
    _            <- users.update(_.updated(username, user.copy(email = newEmail)))
  } yield ()
}

object UserService {

  // Initial test data
  private val initialUsers = Map(
    "john"  -> User("john", Secret("password123"), "john@example.com"),
    "jane"  -> User("jane", Secret("secret456"), "jane@example.com"),
    "admin" -> User("admin", Secret("admin123"), "admin@company.com"),
  )

  val live: ZLayer[Any, Nothing, UserService] =
    ZLayer.fromZIO {
      Ref.make(initialUsers).map(UserServiceLive(_))
    }
}
