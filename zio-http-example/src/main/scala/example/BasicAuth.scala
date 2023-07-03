package example

import zio._

import zio.http.HttpAppMiddleware.basicAuth
import zio.http._
import zio.http.codec.PathCodec.string

object BasicAuth extends ZIOAppDefault {

  // Http app that requires a JWT claim
  val user: App[Any] = Routes(Method.GET / "user" / string("name") / "greet" -> ({ case (name: String) =>
    handler(Response.text(s"Welcome to the ZIO party! ${name}"))
  }: (String) => Handler[Any, Nothing, Request, Response])).toApp

  // Composing all the HttpApps together
  val app: App[Any] = user @@ basicAuth("admin", "admin")

  // Run it like any simple app
  val run = Server.serve(app).provide(Server.default)
}
