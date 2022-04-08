package example.jsonAPI

import zhttp.http._
import zhttp.service.Server
import zio.{App, ExitCode, Has, URIO}

/**
 * The actual Http application that takes in a UserRequest and produces a
 * UserResponse.
 */
object JsonWebAPI extends App {

  type UserService = Has[UserService.Service]

  val app: Http[UserService, Nothing, UserRequest, UserResponse] =
    Http.collectZIO[UserRequest] {
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

  val httpApplication: Http[UserService, Throwable, Request, Response] = app @@ Routing.codec

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, httpApplication).provideCustomLayer(UserService.memory).exitCode

}
