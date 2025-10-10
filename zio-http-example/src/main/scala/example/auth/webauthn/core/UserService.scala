package example.auth.webauthn.core

import zio._

import example.auth.webauthn.core.UserServiceError._
import example.auth.webauthn.model.UserCredential

case class User(
  userHandle: String,
  username: String,
  credentials: Set[UserCredential],
)

sealed trait UserServiceError

object UserServiceError {
  case class UserNotFound(username: String)      extends UserServiceError
  case class UserAlreadyExists(username: String) extends UserServiceError
}

trait UserService {
  def getUser(username: String): IO[UserNotFound, User]
  def getUserByHandle(userHandle: String): IO[UserNotFound, User]
  def addUser(user: User): IO[UserAlreadyExists, Unit]
  def addCredential(userHandle: String, credential: UserCredential): IO[UserNotFound, Unit]
  def getCredentialById(credentialId: String): IO[Nothing, Set[UserCredential]]
}

case class UserServiceLive(users: Ref[Map[String, User]]) extends UserService {

  override def getUser(username: String): IO[UserNotFound, User] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.get(username)).orElseFail(UserNotFound(username))
    }

  override def addUser(user: User): IO[UserAlreadyExists, Unit] =
    users.get.flatMap { userMap =>
      ZIO.when(userMap.contains(user.username)) {
        ZIO.fail(UserAlreadyExists(user.username))
      } *> users.update(_.updated(user.username, user))
    }

  override def addCredential(userHandle: String, credential: UserCredential): IO[UserNotFound, Unit] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.values.find(_.userHandle == userHandle)).orElseFail(UserNotFound(userHandle)).flatMap {
        user =>
          val updatedCredentials = user.credentials + credential
          val updatedUser        = user.copy(credentials = updatedCredentials)
          users.update(_.updated(user.username, updatedUser))
      }
    }

  override def getCredentialById(credentialId: String): IO[Nothing, Set[UserCredential]] =
    users.get.map { userMap =>
      userMap.values
        .flatMap(_.credentials)
        .filter(_.credentialId.getBytes.sameElements(credentialId.getBytes))
        .toSet
    }

  override def getUserByHandle(userHandle: String): IO[UserNotFound, User] =
    users.get.flatMap { userMap =>
      ZIO.fromOption(userMap.values.find(_.userHandle == userHandle)).orElseFail(UserNotFound(userHandle))
    }

}

object UserService {
  def make() =
    Ref.make(Map.empty[String, User]).map(UserServiceLive(_))

  val live: ZLayer[Any, Nothing, UserService] =
    ZLayer.fromZIO(make())
}
