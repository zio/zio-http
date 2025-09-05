//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._

object HelloWorld extends ZIOAppDefault {
  // Responds with plain text
  val homeRoute =
    Method.GET / Root -> handler(Response.text("Hello World!"))

  // Responds with JSON
  val jsonRoute =
    Method.GET / "json" -> handler(Response.json("""{"greetings": "Hello World!"}"""))

  // Create HTTP route
  val app = Routes(homeRoute, jsonRoute)

  // Run it like any simple app
  override val run = Server.serve(app).provide(Server.default)
}
