package zio.http.api

import zio._
import zio.test._
import zio.http.{Request, Response, URL}
import zio.http.api.internal._
import In._

object InSpec extends ZIOSpecDefault {

  def spec = suite("InSpec")(
    suite("handler")(
      test("simple request") {
        val testRoutes = testApi(
          API
            .get(literal("users") / int)
            .output[String]
            .handle { userId =>
              ZIO.succeed(s"route(users, $userId)")
            },
          API
            .get(literal("users") / int / literal("posts") / int)
            .in(query("name"))
            .output[String]
            .handle { case (userId, postId, name) =>
              ZIO.succeed(s"route(users, $userId, posts, $postId) query(name=$name)")
            },
        ) _
        testRoutes("/users/123", "route(users, 123)") &&
        testRoutes("/users/123/posts/555?name=adam", "route(users, 123, posts, 555) query(name=adam)")
      },
      test("out of order api") {
        val testRoutes = testApi(
          API
            .get(literal("users") / int)
            .output[String]
            .handle { userId =>
              ZIO.succeed(s"route(users, $userId)")
            },
          API
            .get(literal("users") / int)
            .in(query("name"))
            .in(literal("posts") / int)
            .in(query("age"))
            .output[String]
            .handle { case (userId, name, postId, age) =>
              ZIO.succeed(s"route(users, $userId, posts, $postId) query(name=$name, age=$age)")
            },
        ) _
        testRoutes("/users/123", "route(users, 123)") &&
        testRoutes(
          "/users/123/posts/555?name=adam&age=9000",
          "route(users, 123, posts, 555) query(name=adam, age=9000)",
        )
      },
    ),
  )

  def testApi[R, E](routes: HandledAPI[R, E, _, _]*)(
    url: String,
    expected: String,
  ): ZIO[R, E, TestResult] = {
    val tree: HandlerTree[R, E] = HandlerTree.fromIterable(routes.map(cast))
    val request                 = Request(url = URL.fromString(url).toOption.get)
    val handler                 = tree.lookup(request).get
    for {
      response <- handler.run(request).flatMap(parseResponse)
    } yield assertTrue(response == expected)
  }

  def parseResponse(response: Response): UIO[String] =
    response.body.asString.!

  private def cast[R, E](handled: HandledAPI[R, E, _, _]): HandledAPI[R, E, Request, Response] =
    handled.asInstanceOf[HandledAPI[R, E, Request, Response]]
}
