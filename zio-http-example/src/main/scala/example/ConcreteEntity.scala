//> using dep "dev.zio::zio-http:3.4.1"

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

  val user: Handler[Any, Nothing, CreateUser, UserCreated] =
    Handler.fromFunction[CreateUser] { case CreateUser(_) =>
      UserCreated(2)
    }

  val routes: Routes[Any, Response] =
    user
      .contramap[Request](req => CreateUser(req.path.encode))     // Http[Any, Nothing, Request, UserCreated]
      .map(userCreated => Response.text(userCreated.id.toString)) // Http[Any, Nothing, Request, Response]
      .toRoutes

  // Run it like any simple app
  val run =
    Server.serve(routes).provide(Server.default)
}
