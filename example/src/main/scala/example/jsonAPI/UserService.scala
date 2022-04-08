package example.jsonAPI

import example.jsonAPI.JsonWebAPI.UserService
import zio.{Ref, UIO, ZIO, ZLayer}

import java.util.UUID

object UserService {
  def add(name: String, age: Int): ZIO[UserService, Nothing, User] =
    ZIO.accessM(_.get.add(name, age))

  def delete(id: UUID): ZIO[UserService, Nothing, Boolean] =
    ZIO.accessM(_.get.delete(id))

  def list: ZIO[UserService, Nothing, List[User]] = ZIO.accessM(_.get.list)

  def memory: ZLayer[Any, Nothing, UserService] = Memory.make

  trait Service {
    def add(name: String, age: Int): UIO[User]
    def delete(id: UUID): UIO[Boolean]
    def list: UIO[List[User]]
  }

  final case class Memory(ref: Ref[Map[User.Id, User]]) extends Service {
    override def add(name: String, age: Int): UIO[User] = for {
      uuid <- UIO(UUID.randomUUID())
      user = User(name, age, uuid)
      _ <- ref.update(_ + (uuid -> user))
    } yield user

    override def delete(id: UUID): UIO[Boolean] = ref.modify {
      case m if m.contains(id) => (true, m - id)
      case m                   => (false, m)
    }

    override def list: UIO[List[User]] = ref.get.map(_.values.toList)
  }

  object Memory {
    def make: ZLayer[Any, Nothing, UserService] =
      Ref.make(Map.empty[User.Id, User]).map(new Memory(_)).toLayer
  }
}
