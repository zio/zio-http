package example.jsonAPI

import zio.json.{DeriveJsonDecoder, JsonDecoder}

sealed trait UserRequest

object UserRequest {
  implicit val decoder: JsonDecoder[UserRequest] = DeriveJsonDecoder.gen[UserRequest]

  def create(name: String, age: Int): UserRequest = CreateUser(name, age)

  def delete(id: User.Id): UserRequest = DeleteUser(id)

  def list: UserRequest = ListUser

  final case class DeleteUser(id: User.Id) extends UserRequest

  final case class CreateUser(name: String, age: Int) extends UserRequest

  case object ListUser extends UserRequest
}
