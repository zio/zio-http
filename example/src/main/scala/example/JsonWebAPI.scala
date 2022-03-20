package example

import zhttp.http._
import zhttp.service.Server
import zio._
import zio.json._

import java.util.UUID
import scala.util.{Success, Try}

object JsonWebAPI extends App {

  final case class User(name: String, age: Int, id: User.Id)
  object User {
    type Id = UUID

    implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]
  }

  type UserService = Has[UserService.Service]
  object UserService {
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

    def memory: ZLayer[Any, Nothing, UserService] = Memory.make

    def add(name: String, age: Int): ZIO[UserService, Nothing, User] =
      ZIO.accessM(_.get.add(name, age))

    def delete(id: UUID): ZIO[UserService, Nothing, Boolean] =
      ZIO.accessM(_.get.delete(id))

    def list: ZIO[UserService, Nothing, List[User]] = ZIO.accessM(_.get.list)
  }

  sealed trait UserResponse { self =>
    def widen: UserResponse = self
  }

  object UserResponse {
    final case class UserCreated(user: User) extends UserResponse
    final case class UserDeleted(id: UUID)   extends UserResponse

    final case class UserNotFound(id: UUID) extends UserResponse

    final case class UserList(users: List[User]) extends UserResponse

    def list(users: List[User]): UserResponse = UserList(users)
    def notFound(id: UUID): UserResponse      = UserNotFound(id)
    def deleted(id: UUID): UserResponse       = UserDeleted(id)
    def created(user: User): UserResponse     = UserCreated(user)

    implicit val encoder: JsonEncoder[UserResponse] = DeriveJsonEncoder.gen[UserResponse]
  }

  sealed trait UserRequest { self =>
    def wrapZIO: UIO[UserRequest] = UIO(self)
  }

  object UserRequest {
    case object ListUser                                extends UserRequest
    final case class DeleteUser(id: User.Id)            extends UserRequest
    final case class CreateUser(name: String, age: Int) extends UserRequest

    def list: UserRequest                           = ListUser
    def delete(id: User.Id): UserRequest            = DeleteUser(id)
    def create(name: String, age: Int): UserRequest = CreateUser(name, age)

    implicit val decoder: JsonDecoder[UserRequest] = DeriveJsonDecoder.gen[UserRequest]
  }

  val decoder: Http[UserService, Throwable, Request, UserRequest] = Http.collectZIO[Request] {

    case Method.GET -> !! / "users" / "list" => UserRequest.list.wrapZIO

    case Method.DELETE -> !! / "user" / id =>
      Try(UUID.fromString(id)) match {
        case Success(uuid) => UserRequest.delete(uuid).wrapZIO
        case _             => ZIO.fail(HttpError.BadRequest("Invalid UUID"))
      }

    case req @ Method.PUT -> !! / "user" / "create" =>
      req.bodyAsString flatMap { body =>
        body
          .fromJson[UserRequest]
          .fold(
            err => ZIO.fail(HttpError.BadRequest(err)),
            {
              case req: UserRequest.CreateUser => req.wrapZIO
              case _                           => ZIO.fail(HttpError.BadRequest("Invalid request payload"))
            },
          )
      }
  }

  val encoder: Http[Any, Nothing, UserResponse, Response] =
    Http.collect[UserResponse] {
      case res: UserResponse.UserNotFound => Response.json(res.widen.toJson).setStatus(Status.NotFound)
      case res                            => Response.json(res.toJson)
    }

  val codec: Middleware[UserService, Throwable, UserRequest, UserResponse, Request, Response] =
    decoder \/ encoder

  val app: Http[UserService, Nothing, UserRequest, UserResponse] = Http.collectZIO[UserRequest] {
    case UserRequest.ListUser              => UserService.list.map(UserResponse.UserList)
    case UserRequest.DeleteUser(id)        =>
      UserService
        .delete(id)
        .map {
          case true  => UserResponse.UserDeleted(id)
          case false => UserResponse.UserNotFound(id)
        }
    case UserRequest.CreateUser(name, age) => UserService.add(name, age).map(UserResponse.created)
  }

  val httpApplication: Http[UserService, Throwable, Request, Response] = app @@ codec

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, httpApplication).provideCustomLayer(UserService.memory).exitCode

  case class status(NotFound: Status)
}
