//> using dep "dev.zio::zio-http:3.7.3"

package example.template2

import zio._
import zio.http._
import zio.http.endpoint.AuthType.None
import zio.http.endpoint._
import zio.http.template2._

object EndpointExample extends ZIOAppDefault {
  val endpoint: Endpoint[Unit, Unit, ZNothing, Dom, None] =
    Endpoint(Method.GET / Root).out[Dom](MediaType.text.`html`)

  val page: Dom =
    html(
      head(title("Hello World")),
      body(
        h1("Hello, ZIO HTTP!"),
        p("This is my first template which is integrated with ZIO HTTP Endpoint."),
      ),
    )

  def run =
    Server
      .serve(endpoint.implementHandler(handler((_: Unit) => page)))
      .provide(Server.default)
}
