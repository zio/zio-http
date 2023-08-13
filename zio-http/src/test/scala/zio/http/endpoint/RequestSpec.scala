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

import java.time.Instant

import zio._
import zio.test._

import zio.stream.ZStream

import zio.schema.annotation.validate
import zio.schema.codec.{DecodeError, JsonCodec}
import zio.schema.validation.Validation
import zio.schema.{DeriveSchema, Schema, StandardType}

import zio.http.Header.ContentType
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{query, queryInt}
import zio.http.codec._
import zio.http.endpoint.EndpointSpec.testEndpoint
import zio.http.forms.Fixtures.formField

object RequestSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status

  case class NewPost(value: String)

  case class User(
    @validate(Validation.greaterThan(0))
    id: Int,
  )

  def spec = suite("RequestSpec")(
    suite("handler")(
      test("simple request") {
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .out[String]
              .implement {
                Handler.fromFunction { userId =>
                  s"path(users, $userId)"
                }
              },
            Endpoint(GET / "users" / int("userId") / "posts" / int("postId"))
              .query(query("name"))
              .out[String]
              .implement {
                Handler.fromFunction { case (userId, postId, name) =>
                  s"path(users, $userId, posts, $postId) query(name=$name)"
                }
              },
          ),
        ) _
        testRoutes("/users/123", "path(users, 123)") &&
        testRoutes("/users/123/posts/555?name=adam", "path(users, 123, posts, 555) query(name=adam)")
      },
      test("bad request for failed codec") {
        val endpoint =
          Endpoint(GET / "posts")
            .query(queryInt("id"))
            .out[Int]

        val routes =
          endpoint.implement { Handler.succeed(42) }

        for {
          response <- routes.toHttpApp.runZIO(
            Request.get(URL.decode("/posts?id=notanid").toOption.get),
          )
        } yield assertTrue(extractStatus(response).code == 400)
      },
      test("out of order api") {
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .out[String]
              .implement {
                Handler.fromFunction { userId =>
                  s"path(users, $userId)"
                }
              },
            Endpoint(GET / "users" / int("userId") / "posts" / int("postId"))
              .query(query("name"))
              .query(query("age"))
              .out[String]
              .implement {
                Handler.fromFunction { case (userId, postId, name, age) =>
                  s"path(users, $userId, posts, $postId) query(name=$name, age=$age)"
                }
              },
          ),
        ) _
        testRoutes("/users/123", "path(users, 123)") &&
        testRoutes(
          "/users/123/posts/555?name=adam&age=9000",
          "path(users, 123, posts, 555) query(name=adam, age=9000)",
        )
      },
      test("fallback") {
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users")
              .query(queryInt("userId") | query("userId"))
              .out[String]
              .implement {
                Handler.fromFunction { case (userId: Either[Int, String]) =>
                  val value = userId.fold(_.toString, identity)

                  s"path(users) query(userId=$value)"
                }
              },
          ),
        ) _
        testRoutes("/users?userId=123", "path(users) query(userId=123)") &&
        testRoutes("/users?userId=adam", "path(users) query(userId=adam)")
      },
      test("broad api") {
        val broadUsers              =
          Endpoint(GET / "users").out[String].implement { Handler.succeed("path(users)") }
        val broadUsersId            =
          Endpoint(GET / "users" / int("userId")).out[String].implement {
            Handler.fromFunction { userId =>
              s"path(users, $userId)"
            }
          }
        val boardUsersPosts         =
          Endpoint(GET / "users" / int("userId") / "posts")
            .out[String]
            .implement {
              Handler.fromFunction { userId =>
                s"path(users, $userId, posts)"
              }
            }
        val boardUsersPostsId       =
          Endpoint(GET / "users" / int("userId") / "posts" / int("postId"))
            .out[String]
            .implement {
              Handler.fromFunction { case (userId, postId) =>
                s"path(users, $userId, posts, $postId)"
              }
            }
        val boardUsersPostsComments =
          Endpoint(
            GET /
              "users" / int("userId") / "posts" / int("postId") / "comments",
          )
            .out[String]
            .implement {
              Handler.fromFunction { case (userId, postId) =>
                s"path(users, $userId, posts, $postId, comments)"
              }
            }

        val boardUsersPostsCommentsId        =
          Endpoint(
            GET /
              "users" / int("userId") / "posts" / int("postId") / "comments" / int("commentId"),
          )
            .out[String]
            .implement {
              Handler.fromFunction { case (userId, postId, commentId) =>
                s"path(users, $userId, posts, $postId, comments, $commentId)"
              }
            }
        val broadPosts                       =
          Endpoint(GET / "posts").out[String].implement(Handler.succeed("path(posts)"))
        val broadPostsId                     =
          Endpoint(GET / "posts" / int("postId")).out[String].implement {
            Handler.fromFunction { postId =>
              s"path(posts, $postId)"
            }
          }
        val boardPostsComments               =
          Endpoint(GET / "posts" / int("postId") / "comments")
            .out[String]
            .implement {
              Handler.fromFunction { postId =>
                s"path(posts, $postId, comments)"
              }
            }
        val boardPostsCommentsId             =
          Endpoint(GET / "posts" / int("postId") / "comments" / int("commentId"))
            .out[String]
            .implement {
              Handler.fromFunction { case (postId, commentId) =>
                s"path(posts, $postId, comments, $commentId)"
              }
            }
        val broadComments                    =
          Endpoint(GET / "comments").out[String].implement(Handler.succeed("path(comments)"))
        val broadCommentsId                  =
          Endpoint(GET / "comments" / int("commentId")).out[String].implement {
            Handler.fromFunction { commentId =>
              s"path(comments, $commentId)"
            }
          }
        val broadUsersComments               =
          Endpoint(GET / "users" / int("userId") / "comments")
            .out[String]
            .implement {
              Handler.fromFunction { userId =>
                s"path(users, $userId, comments)"
              }
            }
        val broadUsersCommentsId             =
          Endpoint(GET / "users" / int("userId") / "comments" / int("commentId"))
            .out[String]
            .implement {
              Handler.fromFunction { case (userId, commentId) =>
                s"path(users, $userId, comments, $commentId)"
              }
            }
        val boardUsersPostsCommentsReplies   =
          Endpoint(
            GET /
              "users" / int("userId") / "posts" / int("postId") / "comments" / int("commentId") / "replies",
          )
            .out[String]
            .implement {
              Handler.fromFunction { case (userId, postId, commentId) =>
                s"path(users, $userId, posts, $postId, comments, $commentId, replies)"
              }
            }
        val boardUsersPostsCommentsRepliesId =
          Endpoint(
            GET /
              "users" / int("userId") / "posts" / int("postId") / "comments" / int("commentId") /
              "replies" / int("replyId"),
          )
            .out[String]
            .implement {
              Handler.fromFunction { case (userId, postId, commentId, replyId) =>
                s"path(users, $userId, posts, $postId, comments, $commentId, replies, $replyId)"
              }
            }

        val testRoutes = testEndpoint(
          Routes(
            broadUsers,
            broadUsersId,
            boardUsersPosts,
            boardUsersPostsId,
            boardUsersPostsComments,
            boardUsersPostsCommentsId,
            broadPosts,
            broadPostsId,
            boardPostsComments,
            boardPostsCommentsId,
            broadComments,
            broadCommentsId,
            broadUsersComments,
            broadUsersCommentsId,
            boardUsersPostsCommentsReplies,
            boardUsersPostsCommentsRepliesId,
          ),
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
      test("composite in codecs") {
        val headerOrQuery = HeaderCodec.name[String]("X-Header") | QueryCodec.query("header")

        val endpoint = Endpoint(GET / "test").out[String].inCodec(headerOrQuery)

        val routes = endpoint.implement(Handler.identity)

        val request = Request.get(
          URL
            .decode("/test?header=query-value")
            .toOption
            .get,
        )

        val requestWithHeaderAndQuery = request.addHeader("X-Header", "header-value")

        val requestWithHeader = Request
          .get(
            URL
              .decode("/test")
              .toOption
              .get,
          )
          .addHeader("X-Header", "header-value")

        for {
          response       <- routes.toHttpApp.runZIO(request)
          onlyQuery      <- response.body.asString.orDie
          response       <- routes.toHttpApp.runZIO(requestWithHeader)
          onlyHeader     <- response.body.asString.orDie
          response       <- routes.toHttpApp.runZIO(requestWithHeaderAndQuery)
          headerAndQuery <- response.body.asString.orDie
        } yield assertTrue(
          onlyQuery == """"query-value"""",
          onlyHeader == """"header-value"""",
          headerAndQuery == """"header-value"""",
        )
      },
      test("composite out codecs") {
        val headerOrQuery = HeaderCodec.name[String]("X-Header") | StatusCodec.status(Status.Created)

        val endpoint = Endpoint(GET / "test").query(QueryCodec.queryBool("Created")).outCodec(headerOrQuery)

        val routes =
          endpoint.implement {
            Handler.fromFunction { created =>
              if (created) Right(()) else Left("not created")
            }
          }

        val requestCreated = Request.get(
          URL
            .decode("/test?Created=true")
            .toOption
            .get,
        )

        val requestNotCreated = Request.get(
          URL
            .decode("/test?Created=false")
            .toOption
            .get,
        )

        for {
          notCreated <- routes.toHttpApp.runZIO(requestNotCreated)
          header = notCreated.rawHeader("X-Header").get
          response <- routes.toHttpApp.runZIO(requestCreated)
          value = header == "not created" &&
            extractStatus(notCreated) == Status.Ok &&
            extractStatus(response) == Status.Created
        } yield assertTrue(value)
      },
      suite("request bodies")(
        test("simple input") {
          implicit val newPostSchema: Schema[NewPost] = DeriveSchema.gen[NewPost]

          val endpoint =
            Endpoint(POST / "posts")
              .in[NewPost]
              .out[Int]

          val routes =
            endpoint.implement(Handler.succeed(42))

          val request =
            Request
              .post(
                URL.decode("/posts").toOption.get,
                Body.fromString("""{"value": "My new post!"}"""),
              )

          for {
            response <- routes.toHttpApp.runZIO(request)
            body     <- response.body.asString.orDie
          } yield assertTrue(extractStatus(response).isSuccess) && assertTrue(body == "42")
        },
        test("bad request for failed codec") {
          implicit val newPostSchema: Schema[NewPost] = DeriveSchema.gen[NewPost]

          val endpoint =
            Endpoint(POST / "posts")
              .in[NewPost]
              .out[Int]

          val routes =
            endpoint.implement(Handler.succeed(42))

          for {
            response <- routes.toHttpApp.runZIO(
              Request
                .post(
                  URL.decode("/posts").toOption.get,
                  Body.fromString("""{"vale": "My new post!"}"""),
                ),
            )
          } yield assertTrue(extractStatus(response).code == 400)
        },
      ),
      suite("byte stream input/output")(
        test("responding with a byte stream") {
          for {
            bytes <- Random.nextBytes(1024)
            route =
              Endpoint(GET / "test-byte-stream")
                .outStream[Byte]
                .implement(Handler.succeed(ZStream.fromChunk(bytes).rechunk(16)))

            result   <- route.toHttpApp.runZIO(Request.get(URL.decode("/test-byte-stream").toOption.get)).exit
            response <- result match {
              case Exit.Success(value) => ZIO.succeed(value)
              case Exit.Failure(cause) =>
                cause.failureOrCause match {
                  case Left(response) => ZIO.succeed(response)
                  case Right(cause)   => ZIO.failCause(cause)
                }
            }
            body     <- response.body.asChunk.orDie
          } yield assertTrue(
            response.header(ContentType) == Some(ContentType(MediaType.application.`octet-stream`)),
            body == bytes,
          )
        },
        test("responding with a byte stream, custom media type") {
          for {
            bytes <- Random.nextBytes(1024)
            route =
              Endpoint(GET / "test-byte-stream")
                .outStream[Byte](Status.Ok, MediaType.image.png)
                .implement(Handler.succeed(ZStream.fromChunk(bytes).rechunk(16)))

            result   <- route.toHttpApp.runZIO(Request.get(URL.decode("/test-byte-stream").toOption.get)).exit
            response <- result match {
              case Exit.Success(value) => ZIO.succeed(value)
              case Exit.Failure(cause) =>
                cause.failureOrCause match {
                  case Left(response) => ZIO.succeed(response)
                  case Right(cause)   => ZIO.failCause(cause)
                }
            }
            body     <- response.body.asChunk.orDie
          } yield assertTrue(
            response.header(ContentType) == Some(ContentType(MediaType.image.png)),
            body == bytes,
          )
        },
        test("request body as a byte stream") {
          for {
            bytes <- Random.nextBytes(1024)
            route =
              Endpoint(POST / "test-byte-stream")
                .inStream[Byte]
                .out[Long]
                .implement {
                  Handler.fromFunctionZIO { byteStream =>
                    byteStream.runCount
                  }
                }
            result   <- route.toHttpApp
              .runZIO(Request.post(URL.decode("/test-byte-stream").toOption.get, Body.fromChunk(bytes)))
              .exit
            response <- result match {
              case Exit.Success(value) => ZIO.succeed(value)
              case Exit.Failure(cause) =>
                cause.failureOrCause match {
                  case Left(response) => ZIO.succeed(response)
                  case Right(cause)   => ZIO.failCause(cause)
                }
            }
            body     <- response.body.asString.orDie
          } yield assertTrue(
            body == "1024",
          )
        },
      ),
    ),
  )
}
