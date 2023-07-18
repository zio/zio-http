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

import zio.schema.codec.{DecodeError, JsonCodec}
import zio.schema.{DeriveSchema, Schema, StandardType}

import zio.http.Header.ContentType
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{query, queryInt}
import zio.http.codec._
import zio.http.forms.Fixtures.formField

object EndpointSpec extends ZIOSpecDefault {
  def extractStatus(response: Response): Status = response.status

  case class NewPost(value: String)

  def spec = suite("EndpointSpec")(
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
      test("optional query parameter") {
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(query("details").optional)
              .out[String]
              .implement {
                Handler.fromFunction { case (userId, details) =>
                  s"path(users, $userId, $details)"
                }
              },
          ),
        ) _
        testRoutes("/users/123", "path(users, 123, None)") &&
        testRoutes("/users/123?details=", "path(users, 123, Some())") &&
        testRoutes("/users/123?details=456", "path(users, 123, Some(456))")
      },
      test("multiple optional query parameters") {
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(query("key").optional)
              .query(query("value").optional)
              .out[String]
              .implement {
                Handler.fromFunction { case (userId, key, value) =>
                  s"path(users, $userId, $key, $value)"
                }
              },
          ),
        ) _
        testRoutes("/users/123", "path(users, 123, None, None)") &&
        testRoutes("/users/123?key=&value=", "path(users, 123, Some(), Some())") &&
        testRoutes("/users/123?key=&value=X", "path(users, 123, Some(), Some(X))") &&
        testRoutes("/users/123?key=X&value=Y", "path(users, 123, Some(X), Some(Y))")
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
      suite("404")(
        test("on wrong path") {
          val testRoutes = test404(
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
          testRoutes("/user/123", Method.GET) &&
          testRoutes("/users/123/wrong", Method.GET)
        },
        test("on wrong method") {
          val testRoutes = test404(
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
          testRoutes("/users/123", Method.POST) &&
          testRoutes("/users/123/posts/555?name=adam", Method.PUT)
        },
      ),
      suite("custom error")(
        test("simple custom error response") {
          val routes =
            Endpoint(GET / "users" / int("userId"))
              .out[String]
              .outError[String](Status.Custom(999))
              .implement {
                Handler.fromFunctionZIO { userId =>
                  ZIO.fail(s"path(users, $userId)")
                }
              }

          val request =
            Request
              .get(
                URL.decode("/users/123").toOption.get,
              )

          for {
            response <- routes.toHttpApp.runZIO(request)
            body     <- response.body.asString.orDie
          } yield assertTrue(extractStatus(response).code == 999, body == "\"path(users, 123)\"")
        },
        test("status depending on the error subtype") {
          val routes =
            Endpoint(GET / "users" / int("userId"))
              .out[String]
              .outErrors[TestError](
                HttpCodec.error[TestError.UnexpectedError](Status.InternalServerError),
                HttpCodec.error[TestError.InvalidUser](Status.NotFound),
              )
              .implement {
                Handler.fromFunctionZIO { userId =>
                  if (userId == 123) ZIO.fail(TestError.InvalidUser(userId))
                  else ZIO.fail(TestError.UnexpectedError("something went wrong"))
                }
              }

          val request1 = Request.get(URL.decode("/users/123").toOption.get)
          val request2 = Request.get(URL.decode("/users/321").toOption.get)

          for {
            response1 <- routes.toHttpApp.runZIO(request1)
            body1     <- response1.body.asString.orDie

            response2 <- routes.toHttpApp.runZIO(request2)
            body2     <- response2.body.asString.orDie
          } yield assertTrue(
            extractStatus(response1) == Status.NotFound,
            body1 == "{\"userId\":123}",
            extractStatus(response2) == Status.InternalServerError,
            body2 == "{\"message\":\"something went wrong\"}",
          )
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
      suite("multipart/form-data")(
        test("multiple outputs produce multipart response") {
          for {
            bytes <- Random.nextBytes(1024)
            route =
              Endpoint(GET / "test-form")
                .outCodec(
                  HttpCodec.contentStream[Byte]("image", MediaType.image.png) ++
                    HttpCodec.content[String]("title") ++
                    HttpCodec.content[Int]("width") ++
                    HttpCodec.content[Int]("height") ++
                    HttpCodec.content[ImageMetadata]("metadata"),
                )
                .implement {
                  Handler.succeed(
                    (
                      ZStream.fromChunk(bytes),
                      "example",
                      320,
                      200,
                      ImageMetadata("some description", Instant.parse("2020-01-01T00:00:00Z")),
                    ),
                  )
                }
            result   <- route.toHttpApp.runZIO(Request.get(URL.decode("/test-form").toOption.get)).exit
            response <- result match {
              case Exit.Success(value) => ZIO.succeed(value)
              case Exit.Failure(cause) =>
                cause.failureOrCause match {
                  case Left(response) => ZIO.succeed(response)
                  case Right(cause)   => ZIO.failCause(cause)
                }
            }
            form     <- response.body.asMultipartForm.orDie
            mediaType = response.header(Header.ContentType).map(_.mediaType)
          } yield assertTrue(
            mediaType == Some(MediaType.multipart.`form-data`),
            form.formData.size == 5,
            form.formData.map(_.name).toSet == Set("image", "title", "width", "height", "metadata"),
            form.get("image").map(_.contentType) == Some(MediaType.image.png),
            form.get("image").map(_.asInstanceOf[FormField.Binary].data) == Some(bytes),
            form.get("title").map(_.asInstanceOf[FormField.Text].value) == Some("example"),
            form.get("width").map(_.asInstanceOf[FormField.Text].value) == Some("320"),
            form.get("height").map(_.asInstanceOf[FormField.Text].value) == Some("200"),
            form.get("metadata").map(_.asInstanceOf[FormField.Binary].data) == Some(
              Chunk.fromArray("""{"description":"some description","createdAt":"2020-01-01T00:00:00Z"}""".getBytes),
            ),
          )
        },
        test("outputs without name get automatically generated names") {
          for {
            bytes <- Random.nextBytes(1024)
            route =
              Endpoint(GET / "test-form")
                .outCodec(
                  HttpCodec.contentStream[Byte](MediaType.image.png) ++
                    HttpCodec.content[String] ++
                    HttpCodec.content[Int] ++
                    HttpCodec.content[Int] ++
                    HttpCodec.content[ImageMetadata],
                )
                .implement {
                  Handler.succeed(
                    (
                      ZStream.fromChunk(bytes),
                      "example",
                      320,
                      200,
                      ImageMetadata("some description", Instant.parse("2020-01-01T00:00:00Z")),
                    ),
                  )
                }
            result   <- route.toHttpApp.runZIO(Request.get(URL.decode("/test-form").toOption.get)).exit
            response <- result match {
              case Exit.Success(value) => ZIO.succeed(value)
              case Exit.Failure(cause) =>
                cause.failureOrCause match {
                  case Left(response) => ZIO.succeed(response)
                  case Right(cause)   => ZIO.failCause(cause)
                }
            }
            form     <- response.body.asMultipartForm.orDie
            mediaType = response.header(Header.ContentType).map(_.mediaType)
          } yield assertTrue(
            mediaType == Some(MediaType.multipart.`form-data`),
            form.formData.size == 5,
            form.formData.map(_.name).toSet == Set("field0", "field1", "field2", "field3", "field4"),
          )
        },
        test("multiple inputs got decoded from multipart/form-data body") {
          for {
            bytes <- Random.nextBytes(1024)
            route =
              Endpoint(POST / "test-form")
                .inStream[Byte]("uploaded-image")
                .in[String]("title")
                .in[ImageMetadata]("metadata")
                .out[(Long, String, ImageMetadata)]
                .implement {
                  Handler.fromFunctionZIO { case (stream, title, metadata) =>
                    stream.runCount.map(count => (count, title, metadata))
                  }
                }
            form  = Form(
              FormField.simpleField("title", "Hello world"),
              FormField.binaryField(
                "metadata",
                Chunk.fromArray("""{"description":"some description","createdAt":"2020-01-01T00:00:00Z"}""".getBytes),
                MediaType.application.json,
              ),
              FormField.binaryField("uploaded-image", bytes, MediaType.image.png),
            )
            boundary <- Boundary.randomUUID
            result   <- route.toHttpApp
              .runZIO(
                Request.post(URL.decode("/test-form").toOption.get, Body.fromMultipartForm(form, boundary)),
              )
              .exit
            response <- result match {
              case Exit.Success(value) => ZIO.succeed(value)
              case Exit.Failure(cause) =>
                cause.failureOrCause match {
                  case Left(response) => ZIO.succeed(response)
                  case Right(cause)   => ZIO.failCause(cause)
                }
            }
            result   <- response.body.asString.orDie
          } yield assertTrue(
            result == """[[1024,"Hello world"],{"description":"some description","createdAt":"2020-01-01T00:00:00Z"}]""",
          )
        },
        test("multipart input/output roundtrip check") {
          check(Gen.chunkOfBounded(2, 8)(formField)) { fields =>
            val endpoint =
              fields.foldLeft(
                Endpoint(POST / "test-form")
                  .copy(output = HttpCodec.status(Status.Ok))
                  .asInstanceOf[Endpoint[Any, Any, Any, Any, EndpointMiddleware.None]],
              ) { case (ep, (_, schema, name, isStreaming)) =>
                if (isStreaming)
                  name match {
                    case Some(name) =>
                      ep.copy(
                        input = (ep.input ++ HttpCodec.contentStream(name)(schema))
                          .asInstanceOf[HttpCodec[HttpCodecType.RequestType, Any]],
                        output = (ep.output ++ HttpCodec
                          .contentStream(name)(schema))
                          .asInstanceOf[HttpCodec[HttpCodecType.ResponseType, Any]],
                      )
                    case None       =>
                      ep.copy(
                        input = (ep.input ++ HttpCodec.contentStream(schema))
                          .asInstanceOf[HttpCodec[HttpCodecType.RequestType, Any]],
                        output = (ep.output ++ HttpCodec.contentStream(schema))
                          .asInstanceOf[HttpCodec[HttpCodecType.ResponseType, Any]],
                      )
                  }
                else
                  name match {
                    case Some(name) =>
                      ep.copy(
                        input = (ep.input ++ HttpCodec.content(name)(schema))
                          .asInstanceOf[HttpCodec[HttpCodecType.RequestType, Any]],
                        output = (ep.output ++ HttpCodec.content(name)(schema))
                          .asInstanceOf[HttpCodec[HttpCodecType.ResponseType, Any]],
                      )
                    case None       =>
                      ep.copy(
                        input = (ep.input ++ HttpCodec.content(schema))
                          .asInstanceOf[HttpCodec[HttpCodecType.RequestType, Any]],
                        output = (ep.output ++ HttpCodec.content(schema))
                          .asInstanceOf[HttpCodec[HttpCodecType.ResponseType, Any]],
                      )
                  }
              }
            val route    =
              endpoint.implement(Handler.identity[Any])

            val form =
              Form(
                fields.map(_._1).zipWithIndex.map { case (field, idx) =>
                  if (field.name.isEmpty) field.name(s"field$idx") else field
                },
              )

            for {
              boundary   <- Boundary.randomUUID
              result     <- route.toHttpApp
                .runZIO(
                  Request.post(URL.decode("/test-form").toOption.get, Body.fromMultipartForm(form, boundary)),
                )
                .exit
              response   <- result match {
                case Exit.Success(value) => ZIO.succeed(value)
                case Exit.Failure(cause) =>
                  cause.failureOrCause match {
                    case Left(response) => ZIO.succeed(response)
                    case Right(cause)   => ZIO.failCause(cause)
                  }
              }
              resultForm <- response.body.asMultipartForm.orDie
              mediaType = response.header(Header.ContentType).map(_.mediaType)

              normalizedIn  <- ZIO.foreach(form.formData) { field =>
                field.asChunk.map(field.name -> _)
              }
              normalizedOut <- ZIO.foreach(resultForm.formData) { field =>
                field.asChunk.map(field.name -> _)
              }
            } yield assertTrue(
              mediaType == Some(MediaType.multipart.`form-data`),
              normalizedIn == normalizedOut,
            )
          }
        },
      ),
    ),
    suite("Examples spec") {
      test("add examples to endpoint") {
        val endpoint     = Endpoint(GET / "repos" / string("org"))
          .out[String]
          .examplesIn("zio")
          .examplesOut("all, zio, repos")
        val endpoint2    =
          Endpoint(GET / "repos" / string("org") / string("repo"))
            .out[String]
            .examplesIn(("zio", "http"), ("zio", "zio"))
            .examplesOut("zio, http")
        val inExamples1  = endpoint.examplesIn
        val outExamples1 = endpoint.examplesOut
        val inExamples2  = endpoint2.examplesIn
        val outExamples2 = endpoint2.examplesOut
        assertTrue(
          inExamples1 == Chunk("zio"),
          outExamples1 == Chunk("all, zio, repos"),
          inExamples2 == Chunk(("zio", "http"), ("zio", "zio")),
          outExamples2 == Chunk("zio, http"),
        )
      }
    },
  )

  def testEndpoint[R](service: Routes[R, Nothing])(
    url: String,
    expected: String,
  ): ZIO[R, Response, TestResult] = {
    val request = Request.get(url = URL.decode(url).toOption.get)
    for {
      response <- service.toHttpApp.runZIO(request)
      body     <- response.body.asString.orDie
    } yield assertTrue(body == "\"" + expected + "\"") // TODO: Real JSON Encoding
  }

  def test404[R](service: Routes[R, Nothing])(
    url: String,
    method: Method,
  ): ZIO[R, Response, TestResult] = {
    val request = Request(method = method, url = URL.decode(url).toOption.get)
    for {
      error <- service.toHttpApp.runZIO(request).flip
      result = error.status == Status.NotFound
    } yield assertTrue(result)
  }

  def parseResponse(response: Response): UIO[String] =
    response.body.asString.!

  sealed trait TestError
  object TestError {
    final case class InvalidUser(userId: Int)         extends TestError
    final case class UnexpectedError(message: String) extends TestError

    implicit val invalidUserSchema: Schema[TestError.InvalidUser]         = DeriveSchema.gen[TestError.InvalidUser]
    implicit val unexpectedErrorSchema: Schema[TestError.UnexpectedError] = DeriveSchema.gen[TestError.UnexpectedError]
  }

  final case class ImageMetadata(description: String, createdAt: Instant)
  object ImageMetadata {
    implicit val schema: Schema[ImageMetadata] = DeriveSchema.gen[ImageMetadata]
  }
}
