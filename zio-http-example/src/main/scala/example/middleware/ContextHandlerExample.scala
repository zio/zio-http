package example.middleware

import zio._
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope
import zio.http._

object ContextHandlerExample extends ZIOAppDefault {

  final case class User(id: String, role: String)
  implicit val userIsNominal: IsNominalType[User] = IsNominalType.derived[User]

  // Example using contextHandler (flagship API) to build a handler that extracts User from context
  val userHandler = contextHandler { (req: Request, user: User) =>
    Response.text(s"User ${user.id} with role ${user.role}")
  }

  val routes: Routes[User] = Routes(
    Method.GET / "me" -> userHandler
  )

  // Provide the context via a middleware (using the now type-preserving custom)
  val provideUser: Middleware[Any, User] = Middleware.custom { (_: Request, _: Scope) =>
    (Response.ok, User("42", "admin"))
  }

  val app: Routes[Any] = routes @@ provideUser

  // Serve with the v4 Loom server (H2 over virtual threads). `serve` returns a
  // ServerHandle; hold it open until the application is interrupted.
  val run = ZIO.acquireRelease(
    ZIO.attempt(LoomServer().serve(app, Context.empty)),
  )(handle => ZIO.succeed(handle.shutdownAndWait())).flatMap { handle =>
    Console.printLine(s"Listening on ${handle.bindings.map(_.address).mkString(", ")}").orDie *> ZIO.never
  }
}
