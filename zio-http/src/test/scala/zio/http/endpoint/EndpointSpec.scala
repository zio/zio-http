package zio.http.endpoint

import zio._
import zio.test._

import zio.http.codec.HttpCodec.{int, literal, query, string}
import zio.http.codec._
import zio.http.model.{Method, Status}
import zio.http.{Request, Response, URL}

object EndpointSpec extends ZIOSpecDefault {

  def spec = suite("EndpointSpec")(
    suite("handler")(
      test("simple request") {
        val testRoutes = testEndpoint(
          Endpoint
            .get(literal("users") / int("userId"))
            .out[String]
            .implement { userId =>
              ZIO.succeed(s"path(users, $userId)")
            } ++
            Endpoint
              .get(literal("users") / int("userId") / literal("posts") / int("postId"))
              .query(query("name"))
              .out[String]
              .implement { case (userId, postId, name) =>
                ZIO.succeed(s"path(users, $userId, posts, $postId) query(name=$name)")
              },
        ) _
        testRoutes("/users/123", "path(users, 123)") &&
        testRoutes("/users/123/posts/555?name=adam", "path(users, 123, posts, 555) query(name=adam)")
      },
      test("out of order api") {
        val testRoutes = testEndpoint(
          Endpoint
            .get(literal("users") / int("userId"))
            .out[String]
            .implement { userId =>
              ZIO.succeed(s"path(users, $userId)")
            } ++
            Endpoint
              .get(literal("users") / int("userId"))
              .query(query("name"))
              .path(literal("posts") / int("postId"))
              .query(query("age"))
              .out[String]
              .implement { case (userId, name, postId, age) =>
                ZIO.succeed(s"path(users, $userId, posts, $postId) query(name=$name, age=$age)")
              },
        ) _
        testRoutes("/users/123", "path(users, 123)") &&
        testRoutes(
          "/users/123/posts/555?name=adam&age=9000",
          "path(users, 123, posts, 555) query(name=adam, age=9000)",
        )
      },
      test("fallback") {
        val testRoutes = testEndpoint(
          Endpoint
            .get(literal("users") / (int("userId") | string("userId")))
            .out[String]
            .implement { userId =>
              ZIO.succeed(s"path(users, $userId)")
            },
        ) _
        testRoutes("/users/123", "path(users, Left(123))") &&
        testRoutes("/users/foo", "path(users, Right(foo))")
      },
      test("broad api") {
        val broadUsers              =
          Endpoint.get(literal("users")).out[String].implement { _ => ZIO.succeed("path(users)") }
        val broadUsersId            =
          Endpoint.get(literal("users") / int("userId")).out[String].implement { userId =>
            ZIO.succeed(s"path(users, $userId)")
          }
        val boardUsersPosts         =
          Endpoint
            .get(literal("users") / int("userId") / "posts")
            .out[String]
            .implement { userId =>
              ZIO.succeed(s"path(users, $userId, posts)")
            }
        val boardUsersPostsId       =
          Endpoint
            .get(literal("users") / int("userId") / "posts" / int("postId"))
            .out[String]
            .implement { case (userId, postId) =>
              ZIO.succeed(s"path(users, $userId, posts, $postId)")
            }
        val boardUsersPostsComments =
          Endpoint
            .get(
              literal("users") / int("userId") / "posts" / int("postId") / literal("comments"),
            )
            .out[String]
            .implement { case (userId, postId) =>
              ZIO.succeed(s"path(users, $userId, posts, $postId, comments)")
            }

        val boardUsersPostsCommentsId        =
          Endpoint
            .get(
              literal("users") / int("userId") / "posts" / int("postId") / literal("comments") / int("commentId"),
            )
            .out[String]
            .implement { case (userId, postId, commentId) =>
              ZIO.succeed(s"path(users, $userId, posts, $postId, comments, $commentId)")
            }
        val broadPosts                       =
          Endpoint.get(literal("posts")).out[String].implement { _ => ZIO.succeed("path(posts)") }
        val broadPostsId                     =
          Endpoint.get(literal("posts") / int("postId")).out[String].implement { postId =>
            ZIO.succeed(s"path(posts, $postId)")
          }
        val boardPostsComments               =
          Endpoint
            .get(literal("posts") / int("postId") / "comments")
            .out[String]
            .implement { postId =>
              ZIO.succeed(s"path(posts, $postId, comments)")
            }
        val boardPostsCommentsId             =
          Endpoint
            .get(literal("posts") / int("postId") / "comments" / int("commentId"))
            .out[String]
            .implement { case (postId, commentId) =>
              ZIO.succeed(s"path(posts, $postId, comments, $commentId)")
            }
        val broadComments                    =
          Endpoint.get(literal("comments")).out[String].implement { _ => ZIO.succeed("path(comments)") }
        val broadCommentsId                  =
          Endpoint.get(literal("comments") / int("commentId")).out[String].implement { commentId =>
            ZIO.succeed(s"path(comments, $commentId)")
          }
        val broadUsersComments               =
          Endpoint
            .get(literal("users") / int("userId") / "comments")
            .out[String]
            .implement { userId =>
              ZIO.succeed(s"path(users, $userId, comments)")
            }
        val broadUsersCommentsId             =
          Endpoint
            .get(literal("users") / int("userId") / "comments" / int("commentId"))
            .out[String]
            .implement { case (userId, commentId) =>
              ZIO.succeed(s"path(users, $userId, comments, $commentId)")
            }
        val boardUsersPostsCommentsReplies   =
          Endpoint
            .get(
              literal("users") / int("userId") / "posts" / int("postId") / literal("comments") / int(
                "commentId",
              ) / literal(
                "replies",
              ),
            )
            .out[String]
            .implement { case (userId, postId, commentId) =>
              ZIO.succeed(s"path(users, $userId, posts, $postId, comments, $commentId, replies)")
            }
        val boardUsersPostsCommentsRepliesId =
          Endpoint
            .get(
              literal("users") / int("userId") / "posts" / int("postId") / PathCodec
                .literal("comments") / int("commentId") / PathCodec
                .literal(
                  "replies",
                ) / int("replyId"),
            )
            .out[String]
            .implement { case (userId, postId, commentId, replyId) =>
              ZIO.succeed(s"path(users, $userId, posts, $postId, comments, $commentId, replies, $replyId)")
            }

        val testRoutes = testEndpoint(
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

        testRoutes("/users", "path(users)") &&
        testRoutes("/users/123", "path(users, 123)") &&
        testRoutes("/users/123/posts", "path(users, 123, posts)") &&
        testRoutes("/users/123/posts/555", "path(users, 123, posts, 555)") &&
        testRoutes("/users/123/posts/555/comments", "path(users, 123, posts, 555, comments)") &&
        testRoutes("/users/123/posts/555/comments/777", "path(users, 123, posts, 555, comments, 777)") &&
        testRoutes("/posts", "path(posts)") &&
        testRoutes("/posts/555", "path(posts, 555)") &&
        testRoutes("/posts/555/comments", "path(posts, 555, comments)") &&
        testRoutes("/posts/555/comments/777", "path(posts, 555, comments, 777)") &&
        testRoutes("/comments", "path(comments)") &&
        testRoutes("/comments/777", "path(comments, 777)") &&
        testRoutes("/users/123/comments", "path(users, 123, comments)") &&
        testRoutes("/users/123/comments/777", "path(users, 123, comments, 777)") &&
        testRoutes(
          "/users/123/posts/555/comments/777/replies",
          "path(users, 123, posts, 555, comments, 777, replies)",
        ) &&
        testRoutes(
          "/users/123/posts/555/comments/777/replies/999",
          "path(users, 123, posts, 555, comments, 777, replies, 999)",
        )

      },
      suite("404")(
        test("on wrong path") {
          val testRoutes = testApiError(
            Endpoint
              .get(literal("users") / int("userId"))
              .out[String]
              .implement { userId =>
                ZIO.succeed(s"path(users, $userId)")
              } ++
              Endpoint
                .get(literal("users") / int("userId") / literal("posts") / int("postId"))
                .query(query("name"))
                .out[String]
                .implement { case (userId, postId, name) =>
                  ZIO.succeed(s"path(users, $userId, posts, $postId) query(name=$name)")
                },
          ) _
          testRoutes("/user/123", Method.GET, Status.NotFound) &&
          testRoutes("/users/123/wrong", Method.GET, Status.NotFound)
        },
        test("on wrong method") {
          val testRoutes = testApiError(
            Endpoint
              .get(literal("users") / int("userId"))
              .out[String]
              .implement { userId =>
                ZIO.succeed(s"path(users, $userId)")
              } ++
              Endpoint
                .get(literal("users") / int("userId") / literal("posts") / int("postId"))
                .query(query("name"))
                .out[String]
                .implement { case (userId, postId, name) =>
                  ZIO.succeed(s"path(users, $userId, posts, $postId) query(name=$name)")
                },
          ) _
          testRoutes("/users/123", Method.POST, Status.NotFound) &&
          testRoutes("/users/123/posts/555?name=adam", Method.PUT, Status.NotFound)
        },
      ),
    ),
  )

  def testEndpoint[R, E](service: Routes[R, E, EndpointMiddleware.None])(
    url: String,
    expected: String,
  ): ZIO[R, Response, TestResult] = {
    val request = Request.get(url = URL.fromString(url).toOption.get)
    for {
      response <- service.toApp.runZIO(request).mapError(_.get)
      body     <- response.body.asString.orDie
    } yield assertTrue(body == "\"" + expected + "\"") // TODO: Real JSON Encoding
  }

  def testApiError[R, E](service: Routes[R, E, EndpointMiddleware.None])(
    url: String,
    method: Method,
    expected: Status,
  ): ZIO[R, Response, TestResult] = {
    val request = Request.default(method = method, url = URL.fromString(url).toOption.get)
    for {
      response <- service.toApp.runZIO(request).mapError(_.get)
      status = response.status
    } yield assertTrue(status == expected)
  }

  def parseResponse(response: Response): UIO[String] =
    response.body.asString.!
}
