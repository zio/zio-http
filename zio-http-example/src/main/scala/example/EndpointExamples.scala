//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._
import zio.http.codec.PathCodec.path
import zio.http.codec._
import zio.http.endpoint.AuthType.None
import zio.http.endpoint._
import zio.http.endpoint.openapi.{OpenAPIGen, SwaggerUI}

object EndpointExamples extends ZIOAppDefault {

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    Endpoint(Method.GET / "users" / int("userId")).out[Int]

  val getUserRoute =
    getUser.implement { id => ZIO.succeed(id) }

  val getUserPosts =
    Endpoint(Method.GET / "users" / int("userId") / "posts" / int("postId"))
      .query(HttpCodec.query[String]("name"))
      .out[List[String]]

  val getUserPostsRoute =
    getUserPosts.implement { case (id1: Int, id2: Int, query: String) =>
      ZIO.succeed(List(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query"))
    }

  val openAPI = OpenAPIGen.fromEndpoints(title = "Endpoint Example", version = "1.0", getUser, getUserPosts)

  val routes = Routes(getUserRoute, getUserPostsRoute) ++ SwaggerUI.routes("docs" / "openapi", openAPI)

  val app = routes // (auth.implement(_ => ZIO.unit)(_ => ZIO.unit))

  val request = Request.get(url = URL.decode("/users/1").toOption.get)

  val run = Server.serve(app).provide(Server.default)

  object ClientExample {
    def example(client: Client) = {
      val locator =
        EndpointLocator.fromURL(URL.decode("http://localhost:8080").toOption.get)

      val executor: EndpointExecutor[Any, Unit, Scope] =
        EndpointExecutor(client, locator)

      val x1: Invocation[Int, Int, ZNothing, Int, None] = getUser(42)
      val x2                                            = getUserPosts(42, 200, "adam")

      val result1: ZIO[Scope, Nothing, Int]          = executor(x1)
      val result2: ZIO[Scope, Nothing, List[String]] = executor(x2)

      result1.zip(result2).debug
    }
  }
}
