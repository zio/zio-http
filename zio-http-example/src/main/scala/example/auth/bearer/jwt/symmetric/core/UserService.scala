package example.auth.bearer.jwt.symmetric.core

import zio.Config._
import zio._
import example.auth.session.cookie.core.UserServiceError._
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.schema.{DeriveSchema, Schema}

sealed trait UserRole
object UserRole {
  case object Admin extends UserRole
  case object User  extends UserRole

  implicit val schema: Schema[UserRole]   = DeriveSchema.gen
  implicit val codec: JsonCodec[UserRole] = DeriveJsonCodec.gen
}

case class User(username: String, password: Secret, email: String, role: UserRole)

sealed trait UserServiceError
object UserServiceError {
  case class UserNotFound(username: String)      extends UserServiceError
  case class UserAlreadyExists(username: String) extends UserServiceError
}

trait UserService {
  def getUser(username: String): IO[UserNotFound, User]
  def addUser(user: User): IO[UserAlreadyExists, Unit]
  def updateEmail(username: String, newEmail: String): IO[UserNotFound, Unit]
  def getUsers: UIO[List[User]]
}

case class UserServiceLive(users: Ref[Map[String, User]]) extends UserService {
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
    user         <- ZIO.fromOption(currentUsers.get(username)).orElseFail(UserNotFound(username))
    _            <- users.update(_.updated(username, user.copy(email = newEmail)))
  } yield ()
}

object UserService {
  private val initialUsers = Map(
    "john"  -> User("john", Secret("password123"), "john@example.com", UserRole.User),
    "jane"  -> User("jane", Secret("secret456"), "jane@example.com", UserRole.User),
    "admin" -> User("admin", Secret("admin123"), "admin@company.com", UserRole.Admin),
  )

  val live: ZLayer[Any, Nothing, UserService] =
    ZLayer.fromZIO {
      Ref.make(initialUsers).map(UserServiceLive(_))
    }
}
