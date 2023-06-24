package example

import zio._

import zio.http._

object HelloWorld extends ZIOAppDefault {

  val textRoute =
    Method.GET / "text" -> Handler.from(Response.text("Hello World!"))

  val jsonRoute =
    Method.GET / "json" -> Handler.from(Response.json("""{"greetings": "Hello World!"}"""))

  // Create HTTP route
  val app: App[Any] = Routes(textRoute, jsonRoute).toApp

  // Run it like any simple app
  override val run = Server.serve(app).provide(Server.default)
}
