package example.auth.webauthn2

import example.auth.webauthn2.UserServiceError._
import example.auth.webauthn2.models.UserCredential
import zio._

case class User(userHandle: String, username: String, credentials: Set[UserCredential])

sealed trait UserServiceError extends Throwable

object UserServiceError {
  case class UserNotFound(username: String)      extends UserServiceError
  case class UserAlreadyExists(username: String) extends UserServiceError
}

trait UserService {
  def getUser(username: String): IO[UserServiceError, User]
  def getUserByHandle(handle: String): IO[UserServiceError, User]
  def addUser(user: User): IO[UserServiceError, Unit]
  def addCredential(userHandle: String, credential: UserCredential): IO[UserServiceError, Unit]
}

case class UserServiceLive(users: Ref[Map[String, User]]) extends UserService {

  override def getUser(username: String): IO[UserServiceError, User] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.get(username)).orElseFail(UserNotFound(username))
    }

  override def addUser(user: User): IO[UserServiceError, Unit] =
    users.get.flatMap { userMap =>
      ZIO.when(userMap.contains(user.username)) {
        ZIO.fail(UserAlreadyExists(user.username))
      } *> users.update(_.updated(user.username, user))
    }

  override def addCredential(userHandle: String, credential: UserCredential): IO[UserServiceError, Unit] = {
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.values.find(_.userHandle == userHandle)).orElseFail(UserNotFound(userHandle)).flatMap { user =>
        val updatedCredentials = user.credentials + credential
        val updatedUser        = user.copy(credentials = updatedCredentials)
        users.update(_.updated(user.username, updatedUser))
      }
    }

//    ZIO.debug("add credential: " + credential) *>
//      users.get.flatMap { userMap =>
//        userMap.get(username) match {
//          case Some(user) =>
//            val updatedCredentials = user.credentials + credential
//            val updatedUser        = user.copy(credentials = updatedCredentials)
//            users.update(_.updated(username, updatedUser))
//          case None       =>
//            ZIO.fail(UserNotFound(username))
//        }
//      }
  }

  override def getUserByHandle(handle: String): IO[UserServiceError, User] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.values.find(_.userHandle == handle)).orElseFail(UserNotFound(handle))
    }
}

object UserService {
  def make() =
    Ref.make(Map.empty[String, User]).map(UserServiceLive(_))

  val live: ZLayer[Any, Nothing, UserService] =
    ZLayer.fromZIO(make())
}
