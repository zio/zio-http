package example

import zio._

import zio.http._
import zio.http.Middleware.basicAuth
import zio.http.codec.PathCodec.string

object BasicAuth extends ZIOAppDefault {

  // Http app that requires a JWT claim
  val user: HttpApp2[Any] = Routes(
    Method.GET / "user" / string("name") / "greet" ->
      handler { (name: String, req: Request) =>
        Response.text(s"Welcome to the ZIO party! ${name}")
      },
  ).toApp

  // Composing all the HttpApps together
  val app: HttpApp2[Any] = user @@ basicAuth("admin", "admin")

  // Run it like any simple app
  val run = Server.serve(app).provide(Server.default)
}
