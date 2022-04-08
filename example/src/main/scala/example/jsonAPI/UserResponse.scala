package example.jsonAPI

import zio.json.{DeriveJsonEncoder, JsonEncoder}

import java.util.UUID

sealed trait UserResponse { self =>
  final def widen: UserResponse = self
}

object UserResponse {
  implicit val encoder: JsonEncoder[UserResponse] = DeriveJsonEncoder.gen[UserResponse]

  def created(user: User): UserResponse = UserCreated(user)

  def deleted(id: UUID): UserResponse = UserDeleted(id)

  def list(users: List[User]): UserResponse = UserList(users)

  def notFound(id: UUID): UserResponse = UserNotFound(id)

  final case class UserCreated(user: User) extends UserResponse

  final case class UserDeleted(id: UUID) extends UserResponse

  final case class UserNotFound(id: UUID) extends UserResponse

  final case class UserList(users: List[User]) extends UserResponse
}
