//> using dep "dev.zio::zio-http:3.4.0"

package example.middleware

import zio._
import zio.blocks.context.IsNominalType
import zio.http._

object ContextHandlerExample extends ZIOAppDefault {

  final case class User(id: String, role: String)
  implicit val userIsNominal: IsNominalType[User] = IsNominalType.derived[User]

  // Example using contextHandler (flagship API) to build a handler that extracts User from context
  val userHandler = contextHandler { (req: Request, user: User) =>
    Response.text(s"User ${user.id} with role ${user.role}")
  }

  val routes: Routes[User, Response] = Routes(
    Method.GET / "me" -> userHandler
  )

  // Provide the context via a middleware (using the now type-preserving custom)
  val provideUser: Middleware[Any, User] = Middleware.custom { (_: Request, _: Scope) =>
    (Response.ok, User("42", "admin"))
  }

  val app: Routes[Any, Response] = routes @@ provideUser

  val run = Server.serve(app).provide(Server.default)
}
