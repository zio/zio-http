package example.auth.digest.core

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
  def userExists(username: String): Task[Boolean]
  def getAllUsers: Task[List[User]]
}

case class UserServiceLive(users: Ref[Map[String, User]]) extends UserService {

  def getUser(username: String): IO[UserServiceError, User] = {
    for {
      userMap <- users.get
      user    <- ZIO
        .fromOption(userMap.get(username))
        .orElseFail(UserServiceError.UserNotFound(username))
    } yield user
  }

  def addUser(user: User): IO[UserServiceError, Unit] = {
    for {
      userMap <- users.get
      _       <- ZIO.when(userMap.contains(user.username))(
        ZIO.fail(UserServiceError.UserAlreadyExists(user.username)),
      )
      _       <- users.update(_.updated(user.username, user))
    } yield ()
  }

  def updateEmail(username: String, newEmail: String): IO[UserServiceError, Unit] = {
    for {
      updated <- users.modify { currentUsers =>
        currentUsers.get(username) match {
          case Some(user) =>
            val updatedUser = user.copy(email = newEmail)
            (Right(()), currentUsers.updated(username, updatedUser))
          case None       =>
            (Left(UserServiceError.UserNotFound(username)), currentUsers)
        }
      }
      _       <- ZIO.fromEither(updated)
    } yield ()
  }

  def userExists(username: String): Task[Boolean] = {
    users.get.map(_.contains(username))
  }

  def getAllUsers: Task[List[User]] = {
    users.get.map(_.values.toList)
  }
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
