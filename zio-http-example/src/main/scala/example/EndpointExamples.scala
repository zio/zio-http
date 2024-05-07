package example

import zio._

import zio.http.Header.Authorization
import zio.http._
import zio.http.codec.{HttpCodec, PathCodec}
import zio.http.endpoint.openapi.{OpenAPIGen, SwaggerUI}
import zio.http.endpoint.{Endpoint, EndpointExecutor, EndpointLocator, EndpointMiddleware}

object EndpointExamples extends ZIOAppDefault {
  import HttpCodec.query
  import PathCodec._

  val auth = EndpointMiddleware.auth

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    Endpoint(Method.GET / "users" / int("userId")).out[Int] @@ auth

  val getUserRoute =
    getUser.implement {
      Handler.fromFunction[Int] { id =>
        id
      }
    }

  val getUserPosts =
    Endpoint(Method.GET / "users" / int("userId") / "posts" / int("postId"))
      .query(query("name"))
      .out[List[String]] @@ auth

  val getUserPostsRoute =
    getUserPosts.implement[Any] {
      Handler.fromFunctionZIO[(Int, Int, String)] { case (id1: Int, id2: Int, query: String) =>
        ZIO.succeed(List(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query"))
      }
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

      val executor: EndpointExecutor[Authorization] =
        EndpointExecutor(client, locator, ZIO.succeed(Authorization.Basic("user", "pass")))

      val x1 = getUser(42)
      val x2 = getUserPosts(42, 200, "adam")

      val result1: ZIO[Scope, Nothing, Int]          = executor(x1)
      val result2: ZIO[Scope, Nothing, List[String]] = executor(x2)

      result1.zip(result2).debug
    }
  }
}
