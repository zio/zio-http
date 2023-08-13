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
import zio.schema.validation.Validation
import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.ContentType
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{query, queryInt}
import zio.http.codec._
import zio.http.forms.Fixtures.formField

object EndpointSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status

  case class NewPost(value: String)
  case class PostCreated(id: Int)

  case class User(
    @validate(Validation.greaterThan(0))
    id: Int,
  )

  def spec = suite("EndpointSpec")(
    suite("handler")(
      test("simple request with query parameter") {
        check(Gen.int(1, Int.MaxValue), Gen.int(1, Int.MaxValue), Gen.alphaNumericString) {
          (userId, postId, username) =>
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
            testRoutes(s"/users/$userId", s"path(users, $userId)") &&
            testRoutes(
              s"/users/$userId/posts/$postId?name=$username",
              s"path(users, $userId, posts, $postId) query(name=$username)",
            )
        }
      },
      test("simple request with header") {
        check(Gen.int(1, Int.MaxValue), Gen.int(1, Int.MaxValue), Gen.uuid) { (userId, postId, correlationId) =>
          val testRoutes = testEndpointWithHeaders(
            Routes(
              Endpoint(GET / "users" / int("userId"))
                .header(HeaderCodec.name[java.util.UUID]("X-Correlation-ID"))
                .out[String]
                .implement {
                  Handler.fromFunction { case (userId, correlationId) =>
                    s"path(users, $userId) header(correlationId=$correlationId)"
                  }
                },
              Endpoint(GET / "users" / int("userId") / "posts" / int("postId"))
                .header(HeaderCodec.name[java.util.UUID]("X-Correlation-ID"))
                .out[String]
                .implement {
                  Handler.fromFunction { case (userId, postId, correlationId) =>
                    s"path(users, $userId, posts, $postId) header(correlationId=$correlationId)"
                  }
                },
            ),
          ) _
          testRoutes(
            s"/users/$userId",
            List("X-Correlation-ID" -> correlationId.toString),
            s"path(users, $userId) header(correlationId=$correlationId)",
          ) &&
          testRoutes(
            s"/users/$userId/posts/$postId",
            List("X-Correlation-ID" -> correlationId.toString),
            s"path(users, $userId, posts, $postId) header(correlationId=$correlationId)",
          )
        }
      },
      test("optional query parameter") {
        check(Gen.int(1, Int.MaxValue), Gen.alphaNumericString) { (userId, details) =>
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
          testRoutes(s"/users/$userId", s"path(users, $userId, None)") &&
          testRoutes(s"/users/$userId?details=", s"path(users, $userId, Some())") &&
          testRoutes(s"/users/$userId?details=$details", s"path(users, $userId, Some($details))")
        }
      },
      test("multiple optional query parameters") {
        check(Gen.int(1, Int.MaxValue), Gen.alphaNumericString, Gen.alphaNumericString) { (userId, key, value) =>
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
          testRoutes(s"/users/$userId", s"path(users, $userId, None, None)") &&
          testRoutes(s"/users/$userId?key=&value=", s"path(users, $userId, Some(), Some())") &&
          testRoutes(s"/users/$userId?key=&value=$value", s"path(users, $userId, Some(), Some($value))") &&
          testRoutes(s"/users/$userId?key=$key&value=$value", s"path(users, $userId, Some($key), Some($value))")
        }
      },
      suite("bad request for failed codec")(
        test("query codec") {
          check(Gen.int(1, Int.MaxValue), Gen.boolean) { (id, notAnId) =>
            val endpoint =
              Endpoint(GET / "posts")
                .query(queryInt("id"))
                .out[Int]
            val routes   =
              endpoint.implement {
                Handler.succeed(id)
              }

            for {
              response <- routes.toHttpApp.runZIO(
                Request.get(URL.decode(s"/posts?id=$notAnId").toOption.get),
              )
            } yield assertTrue(extractStatus(response).code == 400)
          }
        },
        test("header codec") {
          check(Gen.int(1, Int.MaxValue), Gen.alphaNumericString) { (id, notACorrelationId) =>
            val endpoint =
              Endpoint(GET / "posts")
                .header(HeaderCodec.name[java.util.UUID]("X-Correlation-ID"))
                .out[Int]
            val routes   =
              endpoint.implement {
                Handler.succeed(id)
              }

            for {
              response <- routes.toHttpApp.runZIO(
                Request.get(URL.decode(s"/posts").toOption.get).addHeader("X-Correlation-ID", notACorrelationId),
              )
            } yield assertTrue(extractStatus(response).code == 400)
          }
        },
      ),
      test("bad request for missing header")(
        check(Gen.int(1, Int.MaxValue)) { id =>
          val endpoint =
            Endpoint(GET / "posts")
              .header(HeaderCodec.name[java.util.UUID]("X-Correlation-ID"))
              .out[Int]
          val routes   =
            endpoint.implement {
              Handler.succeed(id)
            }

          for {
            response <- routes.toHttpApp.runZIO(
              Request.get(URL.decode(s"/posts").toOption.get),
            )
          } yield assertTrue(extractStatus(response).code == 400)
        },
      ),
      test("out of order api") {
        check(Gen.int(1, Int.MaxValue), Gen.int(1, Int.MaxValue), Gen.alphaNumericString, Gen.int(1, Int.MaxValue)) {
          (userId, postId, name, age) =>
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
            testRoutes(s"/users/$userId", s"path(users, $userId)") &&
            testRoutes(
              s"/users/$userId/posts/$postId?name=$name&age=$age",
              s"path(users, $userId, posts, $postId) query(name=$name, age=$age)",
            )
        }
      },
      test("fallback") {
        check(Gen.int(1, Int.MaxValue), Gen.alphaNumericString) { (userId, username) =>
          val testRoutes = testEndpoint(
            Routes(
              Endpoint(GET / "users")
                .query(queryInt("userId") | query("userId"))
                .out[String]
                .implement {
                  Handler.fromFunction { userId =>
                    val value = userId.fold(_.toString, identity)
                    s"path(users) query(userId=$value)"
                  }
                },
            ),
          ) _
          testRoutes(s"/users?userId=$userId", s"path(users) query(userId=$userId)") &&
          testRoutes(s"/users?userId=$username", s"path(users) query(userId=$username)")
        }
      },
      test("broad api") {
        check(Gen.int(1, Int.MaxValue), Gen.int(1, Int.MaxValue), Gen.int(1, Int.MaxValue), Gen.int(1, Int.MaxValue)) {
          (userId, postId, commentId, replyId) =>
            val broadUsers                       =
              Endpoint(GET / "users").out[String](Doc.p("Created user id")).implement {
                Handler.succeed("path(users)")
              }
            val broadUsersId                     =
              Endpoint(GET / "users" / int("userId")).out[String].implement {
                Handler.fromFunction { userId =>
                  s"path(users, $userId)"
                }
              }
            val boardUsersPosts                  =
              Endpoint(GET / "users" / int("userId") / "posts")
                .out[String]
                .implement {
                  Handler.fromFunction { userId =>
                    s"path(users, $userId, posts)"
                  }
                }
            val boardUsersPostsId                =
              Endpoint(GET / "users" / int("userId") / "posts" / int("postId"))
                .out[String]
                .implement {
                  Handler.fromFunction { case (userId, postId) =>
                    s"path(users, $userId, posts, $postId)"
                  }
                }
            val boardUsersPostsComments          =
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
            testRoutes(s"/users/$userId", s"path(users, $userId)") &&
            testRoutes(s"/users/$userId/posts", s"path(users, $userId, posts)") &&
            testRoutes(s"/users/$userId/posts/$postId", s"path(users, $userId, posts, $postId)") &&
            testRoutes(s"/users/$userId/posts/$postId/comments", s"path(users, $userId, posts, $postId, comments)") &&
            testRoutes(
              s"/users/$userId/posts/$postId/comments/$commentId",
              s"path(users, $userId, posts, $postId, comments, $commentId)",
            ) &&
            testRoutes(s"/posts", "path(posts)") &&
            testRoutes(s"/posts/$postId", s"path(posts, $postId)") &&
            testRoutes(s"/posts/$postId/comments", s"path(posts, $postId, comments)") &&
            testRoutes(s"/posts/$postId/comments/$commentId", s"path(posts, $postId, comments, $commentId)") &&
            testRoutes("/comments", "path(comments)") &&
            testRoutes(s"/comments/$commentId", s"path(comments, $commentId)") &&
            testRoutes(s"/users/$userId/comments", s"path(users, $userId, comments)") &&
            testRoutes(s"/users/$userId/comments/$commentId", s"path(users, $userId, comments, $commentId)") &&
            testRoutes(
              s"/users/$userId/posts/$postId/comments/$commentId/replies",
              s"path(users, $userId, posts, $postId, comments, $commentId, replies)",
            ) &&
            testRoutes(
              s"/users/$userId/posts/$postId/comments/$commentId/replies/$replyId",
              s"path(users, $userId, posts, $postId, comments, $commentId, replies, $replyId)",
            )
        }
      },
      test("composite in codecs") {
        check(Gen.alphaNumericString, Gen.alphaNumericString) { (queryValue, headerValue) =>
          val headerOrQuery             = HeaderCodec.name[String]("X-Header") | QueryCodec.query("header")
          val endpoint                  = Endpoint(GET / "test").out[String].inCodec(headerOrQuery)
          val routes                    = endpoint.implement(Handler.identity)
          val request                   = Request.get(
            URL
              .decode(s"/test?header=$queryValue")
              .toOption
              .get,
          )
          val requestWithHeaderAndQuery = request.addHeader("X-Header", headerValue)
          val requestWithHeader         = Request
            .get(
              URL
                .decode("/test")
                .toOption
                .get,
            )
            .addHeader("X-Header", headerValue)

          for {
            response       <- routes.toHttpApp.runZIO(request)
            onlyQuery      <- response.body.asString.orDie
            response       <- routes.toHttpApp.runZIO(requestWithHeader)
            onlyHeader     <- response.body.asString.orDie
            response       <- routes.toHttpApp.runZIO(requestWithHeaderAndQuery)
            headerAndQuery <- response.body.asString.orDie
          } yield assertTrue(
            onlyQuery == s""""$queryValue"""",
            onlyHeader == s""""$headerValue"""",
            headerAndQuery == s""""$headerValue"""",
          )
        }
      },
      test("composite out codecs") {
        val headerOrQuery     = HeaderCodec.name[String]("X-Header") | StatusCodec.status(Status.Created)
        val endpoint          = Endpoint(GET / "test").query(QueryCodec.queryBool("Created")).outCodec(headerOrQuery)
        val routes            =
          endpoint.implement {
            Handler.fromFunction { created =>
              if (created) Right(()) else Left("not created")
            }
          }
        val requestCreated    = Request.get(
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
          check(Gen.string, Gen.int(1, Int.MaxValue)) { (postValue, postId) =>
            implicit val newPostSchema: Schema[NewPost]         = DeriveSchema.gen[NewPost]
            implicit val postCreatedSchema: Schema[PostCreated] = DeriveSchema.gen[PostCreated]

            val endpoint =
              Endpoint(POST / "posts")
                .in[NewPost](Doc.p("New post"))
                .out[PostCreated](Status.Created, MediaType.application.`json`)
            val routes   =
              endpoint.implement(Handler.succeed(PostCreated(postId)))
            val request  =
              Request
                .post(
                  URL.decode("/posts").toOption.get,
                  Body.fromString(s"""{"value": "$postValue"}"""),
                )

            for {
              response <- routes.toHttpApp.runZIO(request)
              code        = extractStatus(response)
              contentType = response.header(Header.ContentType)
              body <- response.body.asString.orDie
            } yield assertTrue(extractStatus(response).isSuccess) &&
              assertTrue(code == Status.Created) &&
              assertTrue(contentType == Some(ContentType(MediaType.application.`json`))) &&
              assertTrue(body == s"""{"id":$postId}""")
          }
        },
        test("bad request for failed codec") {
          check(Gen.string, Gen.int(1, Int.MaxValue)) { (postValue, postId) =>
            implicit val newPostSchema: Schema[NewPost] = DeriveSchema.gen[NewPost]

            val endpoint =
              Endpoint(POST / "posts")
                .in[NewPost]
                .out[Int]
            val routes   =
              endpoint.implement(Handler.succeed(postId))

            for {
              response <- routes.toHttpApp.runZIO(
                Request
                  .post(
                    URL.decode("/posts").toOption.get,
                    Body.fromString(s"""{"vale": "$postValue"}"""),
                  ),
              )
            } yield assertTrue(extractStatus(response).code == 400)
          }
        },
      ),
      suite("404")(
        test("on wrong path") {
          check(Gen.int(1, Int.MaxValue)) { userId =>
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
            testRoutes(s"/user/$userId", Method.GET) &&
            testRoutes(s"/users/$userId/wrong", Method.GET)
          }
        },
        test("on wrong method") {
          check(Gen.int(1, Int.MaxValue), Gen.int(1, Int.MaxValue), Gen.alphaNumericString) { (userId, postId, name) =>
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
            testRoutes(s"/users/$userId", Method.POST) &&
            testRoutes(s"/users/$userId/posts/$postId?name=$name", Method.PUT)
          }
        },
      ),
      suite("custom error")(
        test("simple custom error response") {
          check(Gen.int(1, Int.MaxValue), Gen.int) { (userId, customCode) =>
            val routes  =
              Endpoint(GET / "users" / int("userId"))
                .out[String]
                .outError[String](Status.Custom(customCode))
                .implement {
                  Handler.fromFunctionZIO { userId =>
                    ZIO.fail(s"path(users, $userId)")
                  }
                }
            val request =
              Request
                .get(
                  URL.decode(s"/users/$userId").toOption.get,
                )

            for {
              response <- routes.toHttpApp.runZIO(request)
              body     <- response.body.asString.orDie
            } yield assertTrue(extractStatus(response).code == customCode, body == s""""path(users, $userId)"""")
          }
        },
        test("status depending on the error subtype") {
          check(Gen.int(1, 1000), Gen.int(1001, Int.MaxValue)) { (myUserId, invalidUserId) =>
            val routes =
              Endpoint(GET / "users" / int("userId"))
                .out[String]
                .outErrors[TestError](
                  HttpCodec.error[TestError.UnexpectedError](Status.InternalServerError),
                  HttpCodec.error[TestError.InvalidUser](Status.NotFound),
                )
                .implement {
                  Handler.fromFunctionZIO { userId =>
                    if (userId == myUserId) ZIO.fail(TestError.InvalidUser(userId))
                    else ZIO.fail(TestError.UnexpectedError("something went wrong"))
                  }
                }

            val request1 = Request.get(URL.decode(s"/users/$myUserId").toOption.get)
            val request2 = Request.get(URL.decode(s"/users/$invalidUserId").toOption.get)

            for {
              response1 <- routes.toHttpApp.runZIO(request1)
              body1     <- response1.body.asString.orDie

              response2 <- routes.toHttpApp.runZIO(request2)
              body2     <- response2.body.asString.orDie
            } yield assertTrue(
              extractStatus(response1) == Status.NotFound,
              body1 == s"""{"userId":$myUserId}""",
              extractStatus(response2) == Status.InternalServerError,
              body2 == """{"message":"something went wrong"}""",
            )
          }
        },
        test("validation occurs automatically on schema") {
          check(Gen.int(1, Int.MaxValue)) { userId =>
            implicit val schema: Schema[User] = DeriveSchema.gen[User]

            val routes =
              Endpoint(POST / "users")
                .in[User](Doc.p("User schema with id"))
                .out[String]
                .implement {
                  Handler.fromFunctionZIO { _ =>
                    ZIO.succeed("User ID is greater than 0")
                  }
                }
                .handleErrorCause { cause =>
                  Response.text("Caught: " + cause.defects.headOption.fold("no known cause")(d => d.getMessage))
                }

            val request1 = Request.post(URL.decode("/users").toOption.get, Body.fromString("""{"id":0}"""))
            val request2 = Request.post(URL.decode("/users").toOption.get, Body.fromString(s"""{"id":$userId}"""))

            for {
              response1 <- routes.toHttpApp.runZIO(request1)
              body1     <- response1.body.asString.orDie
              response2 <- routes.toHttpApp.runZIO(request2)
              body2     <- response2.body.asString.orDie
            } yield assertTrue(
              extractStatus(response1) == Status.BadRequest,
              body1 == "",
              extractStatus(response2) == Status.Ok,
              body2 == "\"User ID is greater than 0\"",
            )
          }
        },
      ),
      suite("byte stream input/output")(
        test("responding with a byte stream") {
          check(Gen.chunkOfBounded(1, 1024)(Gen.byte)) { bytes =>
            val route = Endpoint(GET / "test-byte-stream")
              .outStream[Byte](Doc.p("Test data"))
              .implement(Handler.succeed(ZStream.fromChunk(bytes).rechunk(16)))

            for {
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
          }
        },
        test("responding with a byte stream, custom media type") {
          check(Gen.chunkOfBounded(1, 1024)(Gen.byte)) { bytes =>
            val route = Endpoint(GET / "test-byte-stream")
              .outStream[Byte](Status.Ok, MediaType.image.png)
              .implement(Handler.succeed(ZStream.fromChunk(bytes).rechunk(16)))

            for {
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
          }
        },
        test("request body as a byte stream") {
          check(Gen.chunkOfBounded(1, 1024)(Gen.byte)) { bytes =>
            val route = Endpoint(POST / "test-byte-stream")
              .inStream[Byte]
              .out[Long]
              .implement {
                Handler.fromFunctionZIO { byteStream =>
                  byteStream.runCount
                }
              }

            for {
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
            } yield assertTrue(body == bytes.size.toString)
          }
        },
      ),
      suite("multipart/form-data")(
        test("multiple outputs produce multipart response") {
          check(
            Gen.alphaNumericString,
            Gen.int(1, Int.MaxValue),
            Gen.int(1, Int.MaxValue),
            Gen.alphaNumericString,
            Gen.instant,
          ) { (title, width, height, description, createdAt) =>
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
                        title,
                        width,
                        height,
                        ImageMetadata(description, createdAt),
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
              form.get("title").map(_.asInstanceOf[FormField.Text].value) == Some(title),
              form.get("width").map(_.asInstanceOf[FormField.Text].value) == Some(width.toString),
              form.get("height").map(_.asInstanceOf[FormField.Text].value) == Some(height.toString),
              form.get("metadata").map(_.asInstanceOf[FormField.Binary].data) == Some(
                Chunk.fromArray(s"""{"description":"$description","createdAt":"$createdAt"}""".getBytes),
              ),
            )
          }
        },
        test("outputs without name get automatically generated names") {
          check(
            Gen.alphaNumericString,
            Gen.int(1, Int.MaxValue),
            Gen.int(1, Int.MaxValue),
            Gen.alphaNumericString,
            Gen.instant,
          ) { (title, width, height, description, createdAt) =>
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
                        title,
                        width,
                        height,
                        ImageMetadata(description, createdAt),
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
              form.get("field0").map(_.contentType) == Some(MediaType.image.png),
              form.get("field0").map(_.asInstanceOf[FormField.Binary].data) == Some(bytes),
              form.get("field1").map(_.asInstanceOf[FormField.Text].value) == Some(title),
              form.get("field2").map(_.asInstanceOf[FormField.Text].value) == Some(width.toString),
              form.get("field3").map(_.asInstanceOf[FormField.Text].value) == Some(height.toString),
              form.get("field4").map(_.asInstanceOf[FormField.Binary].data) == Some(
                Chunk.fromArray(s"""{"description":"$description","createdAt":"$createdAt"}""".getBytes),
              ),
            )
          }
        },
        test("multiple inputs got decoded from multipart/form-data body") {
          check(Gen.alphaNumericString, Gen.alphaNumericString, Gen.instant) { (title, description, createdAt) =>
            for {
              bytes <- Random.nextBytes(1024)
              route =
                Endpoint(POST / "test-form")
                  .inStream[Byte]("uploaded-image", Doc.p("Image data"))
                  .in[String]("title")
                  .in[ImageMetadata]("metadata", Doc.p("Image metadata with description and creation date and time"))
                  .out[(Long, String, ImageMetadata)]
                  .implement {
                    Handler.fromFunctionZIO { case (stream, title, metadata) =>
                      stream.runCount.map(count => (count, title, metadata))
                    }
                  }
              form  = Form(
                FormField.simpleField("title", title),
                FormField.binaryField(
                  "metadata",
                  Chunk.fromArray(
                    s"""{"description":"$description","createdAt":"$createdAt"}""".getBytes,
                  ),
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
              result == s"""[[1024,"$title"],{"description":"$description","createdAt":"$createdAt"}]""",
            )
          }
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
    suite("examples")(
      test("add examples to endpoint") {
        check(Gen.alphaNumericString, Gen.alphaNumericString) { (repo1, repo2) =>
          val endpoint  = Endpoint(GET / "repos" / string("org"))
            .out[String]
            .examplesIn("org" -> "zio")
            .examplesOut("repos" -> s"all, zio, repos, $repo1, $repo2")
          val endpoint2 =
            Endpoint(GET / "repos" / string("org") / string("repo"))
              .out[String]
              .examplesIn(
                "org/repo1" -> ("zio", "http"),
                "org/repo2" -> ("zio", "zio"),
                "org/repo3" -> ("zio", repo1),
                "org/repo4" -> ("zio", repo2),
              )
              .examplesOut("repos" -> s"zio, http, $repo1, $repo2")
          assertTrue(
            endpoint.examplesIn == Map("org" -> "zio"),
            endpoint.examplesOut == Map("repos" -> s"all, zio, repos, $repo1, $repo2"),
            endpoint2.examplesIn == Map(
              "org/repo1" -> ("zio", "http"),
              "org/repo2" -> ("zio", "zio"),
              "org/repo3" -> ("zio", repo1),
              "org/repo4" -> ("zio", repo2),
            ),
            endpoint2.examplesOut == Map("repos" -> s"zio, http, $repo1, $repo2"),
          )
        }
      },
    ),
  )

  def testEndpointWithHeaders[R](service: Routes[R, Nothing])(
    url: String,
    headers: List[(String, String)],
    expected: String,
  ): ZIO[R, Response, TestResult] = {
    val request = Request
      .get(url = URL.decode(url).toOption.get)
      .addHeaders(headers.foldLeft(Headers.empty) { case (hs, (k, v)) => hs ++ Headers(k, v) })
    for {
      response <- service.toHttpApp.runZIO(request)
      body     <- response.body.asString.orDie
    } yield assertTrue(body == "\"" + expected + "\"") // TODO: Real JSON Encoding
  }

  def testEndpoint[R](service: Routes[R, Nothing])(
    url: String,
    expected: String,
  ): ZIO[R, Response, TestResult] =
    testEndpointWithHeaders(service)(url, headers = List.empty, expected)

  def test404[R](service: Routes[R, Nothing])(
    url: String,
    method: Method,
  ): ZIO[R, Response, TestResult] = {
    val request = Request(method = method, url = URL.decode(url).toOption.get)
    for {
      response <- service.toHttpApp.runZIO(request)
      result = response.status == Status.NotFound
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
