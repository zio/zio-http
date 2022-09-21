package zio.http.api

import zio._
import zio.http.api.In._
import zio.http.api.internal.HandlerTree
import zio.http.{Request, Response, URL}
import zio.test._

object InSpec extends ZIOSpecDefault {

  def spec = suite("InSpec")(
    suite("handler")(
      test("simple request") {
        val testRoutes = testApi(
          API
            .get(literal("users") / int)
            .out[String]
            .handle { userId =>
              ZIO.succeed(s"route(users, $userId)")
            } ++
            API
              .get(literal("users") / int / literal("posts") / int)
              .in(query("name"))
              .out[String]
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
            .out[String]
            .handle { userId =>
              ZIO.succeed(s"route(users, $userId)")
            } ++
            API
              .get(literal("users") / int)
              .in(query("name"))
              .in(literal("posts") / int)
              .in(query("age"))
              .out[String]
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

  def testApi[R, E](service: Service[R, E, _])(
    url: String,
    expected: String,
  ): ZIO[R, E, TestResult] = {

    val request = Request(url = URL.fromString(url).toOption.get)
    for {
      response <- service.toHttpApp(request).mapError(_.get)
      body     <- response.body.asString.orDie
    } yield assertTrue(body == "\"" + expected + "\"") // TODO: Real JSON Encoding
  }

  def parseResponse(response: Response): UIO[String] =
    response.body.asString.!
}
