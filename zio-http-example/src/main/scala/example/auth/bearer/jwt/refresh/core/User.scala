package example.auth.bearer.jwt.refresh.core

import zio.Config._
import zio._

case class User(username: String, password: Secret, email: String, roles: Set[String])

sealed trait UserServiceError
object UserServiceError {
  case class UserNotFound(username: String) extends UserServiceError
  case class UserAlreadyExists(username: String) extends UserServiceError
}

trait UserService {
  def getUser(username: String): IO[UserServiceError.UserNotFound, User]
  def addUser(user: User): IO[UserServiceError.UserAlreadyExists, Unit]
  def updateEmail(username: String, newEmail: String): IO[UserServiceError.UserNotFound, Unit]
  def getUsers: UIO[List[User]]
  def validateCredentials(username: String, password: String): IO[UserServiceError.UserNotFound, User]
}

case class UserServiceLive(users: Ref[Map[String, User]]) extends UserService {
  import UserServiceError._

  def getUsers: UIO[List[User]] =
    users.get.map(_.values.toList)

  def getUser(username: String): IO[UserNotFound, User] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.get(username)).orElseFail(UserNotFound(username))
    }

  def addUser(user: User): IO[UserAlreadyExists, Unit] =
    users.get.flatMap { userMap =>
      ZIO.when(userMap.contains(user.username)) {
        ZIO.fail(UserAlreadyExists(user.username))
      } *> users.update(_.updated(user.username, user))
    }

  def updateEmail(username: String, newEmail: String): IO[UserNotFound, Unit] = for {
    currentUsers <- users.get
    user <- ZIO.fromOption(currentUsers.get(username)).orElseFail(UserNotFound(username))
    _ <- users.update(_.updated(username, user.copy(email = newEmail)))
  } yield ()

  def validateCredentials(username: String, password: String): IO[UserNotFound, User] =
    for {
      user <- getUser(username)
      _ <- ZIO.when(user.password != Secret(password)) {
        ZIO.fail(UserNotFound(username)) // Using UserNotFound to indicate invalid credentials
      }
    } yield user
}

object UserService {
  private val initialUsers = Map(
    "john" -> User("john", Secret("password123"), "john@example.com", Set("user")),
    "jane" -> User("jane", Secret("secret456"), "jane@example.com", Set("user")),
    "admin" -> User("admin", Secret("admin123"), "admin@company.com", Set("user", "admin"))
  )

  val live: ZLayer[Any, Nothing, UserService] =
    ZLayer.fromZIO {
      Ref.make(initialUsers).map(UserServiceLive(_))
    }
}