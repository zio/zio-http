/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.endpoint

import zio._
import zio.test._

import zio.schema.{DeriveSchema, Schema}

import zio.http.codec.HttpCodec.{int, literal, query, string}
import zio.http.codec._
import zio.http.model.Method
import zio.http.{Body, Request, Response, URL}

object EndpointSpec extends ZIOSpecDefault {
  case class NewPost(value: String)

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
      suite("request bodies")(
        test("simple input") {

          implicit val newPostSchema: Schema[NewPost] = DeriveSchema.gen[NewPost]

          val endpoint =
            Endpoint
              .post(literal("posts"))
              .in[NewPost]
              .out[Int]

          val routes =
            endpoint.implement(_ => ZIO.succeed(42))

          val request =
            Request
              .post(
                Body.fromString("""{"value": "My new post!"}"""),
                URL.decode("/posts").toOption.get,
              )

          for {
            response <- routes.toApp.runZIO(request).mapError(_.get)
            body     <- response.body.asString.orDie
          } yield assertTrue(response.status.isSuccess) && assertTrue(body == "42")
        },
      ),
      suite("404")(
        test("on wrong path") {
          val testRoutes = test404(
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
          testRoutes("/user/123", Method.GET) &&
          testRoutes("/users/123/wrong", Method.GET)
        },
        test("on wrong method") {
          val testRoutes = test404(
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
          testRoutes("/users/123", Method.POST) &&
          testRoutes("/users/123/posts/555?name=adam", Method.PUT)
        },
      ),
    ),
  )

  def testEndpoint[R, E](service: Routes[R, E, EndpointMiddleware.None])(
    url: String,
    expected: String,
  ): ZIO[R, Response, TestResult] = {
    val request = Request.get(url = URL.decode(url).toOption.get)
    for {
      response <- service.toApp.runZIO(request).mapError(_.get)
      body     <- response.body.asString.orDie
    } yield assertTrue(body == "\"" + expected + "\"") // TODO: Real JSON Encoding
  }

  def test404[R, E](service: Routes[R, E, EndpointMiddleware.None])(
    url: String,
    method: Method,
  ): ZIO[R, Response, TestResult] = {
    val request = Request.default(method = method, url = URL.decode(url).toOption.get)
    for {
      error <- service.toApp.runZIO(request).flip
    } yield assertTrue(error == None)
  }

  def parseResponse(response: Response): UIO[String] =
    response.body.asString.!
}
