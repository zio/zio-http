//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http.RoutePattern._
import zio.http._
import zio.http.codec.PathCodec._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.endpoint.openapi._

object GreetEndpointExample extends ZIOAppDefault {
  // Endpoint Definition
  val endpoint =
    Endpoint(GET / "greet" ?? Doc.p("Route for greeting"))
      .query(
        HttpCodec.query[String]("name") ?? Doc.p("Name of the person to greet"),
      )
      .out[String]

  // Endpoint Implementation
  val greetRoute: Route[Any, Nothing] =
    endpoint.implementHandler(handler((name: String) => s"Hello, $name!"))

  val openApiRoutes: Routes[Any, Response] =
    SwaggerUI.routes("docs" / "openapi", OpenAPIGen.fromEndpoints(endpoint))

  // Serving Routes
  def run =
    Server
      .serve(Routes(greetRoute) ++ openApiRoutes)
      .provide(Server.default)
}
