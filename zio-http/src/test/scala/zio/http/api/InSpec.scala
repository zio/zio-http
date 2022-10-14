package zio.http.api

import zio._
import zio.http.api.QueryCodec._
import zio.http.api.RouteCodec._
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
      test("broad api") {
        val broadUsers   = API.get(RouteCodec.literal("users")).out[String].handle { _ => ZIO.succeed("route(users)") }
        val broadUsersId =
          API.get(RouteCodec.literal("users") / RouteCodec.int).out[String].handle { userId =>
            ZIO.succeed(s"route(users, $userId)")
          }
        val boardUsersPosts         =
          API.get(RouteCodec.literal("users") / RouteCodec.int / RouteCodec.literal("posts")).out[String].handle {
            userId =>
              ZIO.succeed(s"route(users, $userId, posts)")
          }
        val boardUsersPostsId       =
          API
            .get(RouteCodec.literal("users") / RouteCodec.int / RouteCodec.literal("posts") / RouteCodec.int)
            .out[String]
            .handle { case (userId, postId) =>
              ZIO.succeed(s"route(users, $userId, posts, $postId)")
            }
        val boardUsersPostsComments =
          API
            .get(
              RouteCodec.literal("users") / RouteCodec.int / RouteCodec.literal("posts") / RouteCodec.int / RouteCodec
                .literal("comments"),
            )
            .out[String]
            .handle { case (userId, postId) =>
              ZIO.succeed(s"route(users, $userId, posts, $postId, comments)")
            }

        val boardUsersPostsCommentsId =
          API
            .get(
              RouteCodec.literal("users") / RouteCodec.int / RouteCodec.literal("posts") / RouteCodec.int / RouteCodec
                .literal("comments") / RouteCodec.int,
            )
            .out[String]
            .handle { case (userId, postId, commentId) =>
              ZIO.succeed(s"route(users, $userId, posts, $postId, comments, $commentId)")
            }
        val broadPosts   = API.get(RouteCodec.literal("posts")).out[String].handle { _ => ZIO.succeed("route(posts)") }
        val broadPostsId = API.get(RouteCodec.literal("posts") / RouteCodec.int).out[String].handle { postId =>
          ZIO.succeed(s"route(posts, $postId)")
        }
        val boardPostsComments   =
          API.get(RouteCodec.literal("posts") / RouteCodec.int / RouteCodec.literal("comments")).out[String].handle {
            postId =>
              ZIO.succeed(s"route(posts, $postId, comments)")
          }
        val boardPostsCommentsId =
          API
            .get(RouteCodec.literal("posts") / RouteCodec.int / RouteCodec.literal("comments") / RouteCodec.int)
            .out[String]
            .handle { case (postId, commentId) =>
              ZIO.succeed(s"route(posts, $postId, comments, $commentId)")
            }
        val broadComments        =
          API.get(RouteCodec.literal("comments")).out[String].handle { _ => ZIO.succeed("route(comments)") }
        val broadCommentsId = API.get(RouteCodec.literal("comments") / RouteCodec.int).out[String].handle { commentId =>
          ZIO.succeed(s"route(comments, $commentId)")
        }
        val broadUsersComments               =
          API.get(RouteCodec.literal("users") / RouteCodec.int / RouteCodec.literal("comments")).out[String].handle {
            userId =>
              ZIO.succeed(s"route(users, $userId, comments)")
          }
        val broadUsersCommentsId             =
          API
            .get(RouteCodec.literal("users") / RouteCodec.int / RouteCodec.literal("comments") / RouteCodec.int)
            .out[String]
            .handle { case (userId, commentId) =>
              ZIO.succeed(s"route(users, $userId, comments, $commentId)")
            }
        val boardUsersPostsCommentsReplies   =
          API
            .get(
              RouteCodec.literal("users") / RouteCodec.int / RouteCodec.literal("posts") / RouteCodec.int / RouteCodec
                .literal("comments") / RouteCodec.int / RouteCodec
                .literal(
                  "replies",
                ),
            )
            .out[String]
            .handle { case (userId, postId, commentId) =>
              ZIO.succeed(s"route(users, $userId, posts, $postId, comments, $commentId, replies)")
            }
        val boardUsersPostsCommentsRepliesId =
          API
            .get(
              RouteCodec.literal("users") / RouteCodec.int / RouteCodec.literal("posts") / RouteCodec.int / RouteCodec
                .literal("comments") / RouteCodec.int / RouteCodec
                .literal(
                  "replies",
                ) / RouteCodec.int,
            )
            .out[String]
            .handle { case (userId, postId, commentId, replyId) =>
              ZIO.succeed(s"route(users, $userId, posts, $postId, comments, $commentId, replies, $replyId)")
            }

        val testRoutes = testApi(
          broadUsers ++
            broadUsersId ++
            boardUsersPosts ++
            boardUsersPostsId ++
            boardUsersPostsComments ++
            boardUsersPostsCommentsId ++
            broadPosts ++
            broadPostsId ++
            boardPostsComments ++
            boardPostsCommentsId ++
            broadComments ++
            broadCommentsId ++
            broadUsersComments ++
            broadUsersCommentsId ++
            boardUsersPostsCommentsReplies ++
            boardUsersPostsCommentsRepliesId,
        ) _

        testRoutes("/users", "route(users)") &&
        testRoutes("/users/123", "route(users, 123)") &&
        testRoutes("/users/123/posts", "route(users, 123, posts)") &&
        testRoutes("/users/123/posts/555", "route(users, 123, posts, 555)") &&
        testRoutes("/users/123/posts/555/comments", "route(users, 123, posts, 555, comments)") &&
        testRoutes("/users/123/posts/555/comments/777", "route(users, 123, posts, 555, comments, 777)") &&
        testRoutes("/posts", "route(posts)") &&
        testRoutes("/posts/555", "route(posts, 555)") &&
        testRoutes("/posts/555/comments", "route(posts, 555, comments)") &&
        testRoutes("/posts/555/comments/777", "route(posts, 555, comments, 777)") &&
        testRoutes("/comments", "route(comments)") &&
        testRoutes("/comments/777", "route(comments, 777)") &&
        testRoutes("/users/123/comments", "route(users, 123, comments)") &&
        testRoutes("/users/123/comments/777", "route(users, 123, comments, 777)") &&
        testRoutes(
          "/users/123/posts/555/comments/777/replies",
          "route(users, 123, posts, 555, comments, 777, replies)",
        ) &&
        testRoutes(
          "/users/123/posts/555/comments/777/replies/999",
          "route(users, 123, posts, 555, comments, 777, replies, 999)",
        )

      },
    ),
  )

  def testApi[R, E](service: Service[R, E, _])(
    url: String,
    expected: String,
  ): ZIO[R, E, TestResult] = {
    val request = Request.get(url = URL.fromString(url).toOption.get)
    for {
      response <- service.toHttpApp(request).mapError(_.get)
      body     <- response.body.asString.orDie
    } yield assertTrue(body == "\"" + expected + "\"") // TODO: Real JSON Encoding
  }

  def parseResponse(response: Response): UIO[String] =
    response.body.asString.!
}
