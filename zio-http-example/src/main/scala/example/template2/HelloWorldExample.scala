package example.template2

import zio._

import zio.http._
import zio.http.template2._

object HelloWorldExample extends ZIOAppDefault {

  val page: Dom =
    html(
      head(title("Hello World")),
      body(
        h1("Hello, ZIO HTTP!"),
        p("This is my first template."),
      ),
    )

  val run = Server
    .serve(
      Method.GET / Root -> handler {
        Response.html(page)
      },
    )
    .provide(Server.default)
}
