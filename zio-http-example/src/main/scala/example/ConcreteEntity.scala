package example

import zio._
import zio.http._

/**
 * Example to build app on concrete entity
 */
object ConcreteEntity extends ZIOAppDefault {
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
  val run =
    Server.serve(app).provide(Server.default)
}
