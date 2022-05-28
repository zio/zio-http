package example

import zhttp.http.{Http, HttpApp, Request, Response}
import zhttp.service.Server
import zio.{App, ExitCode, URIO}

/**
 * Example to build app on concrete entity
 */
object ConcreteEntity extends App {
  // Request
  case class CreateUser(name: String)

  // Response
  case class UserCreated(id: Long)

  val user: Http[Any, Nothing, CreateUser, UserCreated] =
    Http.collect[CreateUser] { case CreateUser(_) =>
      UserCreated(2)
    }

  val app: HttpApp[Any, Nothing] =
    user
      .contramap[Request](req => CreateUser(req.path.encode))     // Http[Any, Nothing, Request, UserCreated]
      .map(userCreated => Response.text(userCreated.id.toString)) // Http[Any, Nothing, Request, Response]

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
