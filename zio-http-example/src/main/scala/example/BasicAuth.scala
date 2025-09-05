//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http.Middleware.basicAuth
import zio.http._
import zio.http.codec.PathCodec.string

object BasicAuth extends ZIOAppDefault {

  // Http app that requires basic auth
  val user: Routes[Any, Response] = Routes(
    Method.GET / "user" / string("name") / "greet" ->
      handler { (name: String, _: Request) =>
        Response.text(s"Welcome to the ZIO party! ${name}")
      },
  )

  // Add basic auth middleware
  val routes: Routes[Any, Response] = user @@ basicAuth("admin", "admin")

  val run = Server.serve(routes).provide(Server.default)
}
