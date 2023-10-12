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
import zio.http.endpoint.EndpointSpec.{extractStatus, testEndpoint, testEndpointWithHeaders}
import zio.http.endpoint._
import zio.http.forms.Fixtures.formField

object RequestSpec extends ZIOHttpSpec {
  def spec = suite("RequestSpec")(
    suite("handler")(
      test("simple request with header") {
        check(Gen.int, Gen.int, Gen.uuid) { (userId, postId, correlationId) =>
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
      test("custom content type") {
        check(Gen.int) { id =>
          val endpoint =
            Endpoint(GET / "posts")
              .query(query("id"))
              .out[Int](MediaType.text.`plain`)
          val routes   =
            endpoint.implement {
              Handler.succeed(id)
            }

          for {
            response <- routes.toHttpApp.runZIO(
              Request
                .get(URL.decode(s"/posts?id=$id").toOption.get)
                .addHeader(Header.Accept(MediaType.text.`plain`)),
            )
            contentType = response.header(Header.ContentType)
          } yield assertTrue(extractStatus(response).code == 200) &&
            assertTrue(contentType == Some(ContentType(MediaType.text.`plain`)))
        }
      },
      test("custom status code") {
        check(Gen.int) { id =>
          val endpoint =
            Endpoint(GET / "posts")
              .query(query("id"))
              .out[Int](Status.NotFound)
          val routes   =
            endpoint.implement {
              Handler.succeed(id)
            }

          for {
            response <- routes.toHttpApp.runZIO(
              Request.get(URL.decode(s"/posts?id=$id").toOption.get),
            )
          } yield assertTrue(extractStatus(response).code == 404)
        }
      },
      suite("bad request for failed codec")(
        test("query codec") {
          check(Gen.int, Gen.boolean) { (id, notAnId) =>
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
              contentType = response.header(Header.ContentType)
            } yield assertTrue(extractStatus(response).code == 400) &&
              assertTrue(contentType.isEmpty)
          }
        },
        test("multiple parameters for a single value query") {
          check(Gen.int, Gen.int, Gen.int) { (id, id1, id2) =>
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
                Request.get(URL.decode(s"/posts?id=$id1&id=$id2").toOption.get),
              )
              contentType = response.header(Header.ContentType)
            } yield assertTrue(extractStatus(response).code == 400) &&
              assertTrue(contentType.isEmpty)
          }
        },
        test("header codec") {
          check(Gen.int, Gen.alphaNumericString) { (id, notACorrelationId) =>
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
        check(Gen.int) { id =>
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
        check(Gen.int, Gen.int, Gen.alphaNumericString, Gen.int(1, Int.MaxValue)) { (userId, postId, name, age) =>
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
        check(Gen.int, Gen.alphaNumericString) { (userId, username) =>
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
        check(Gen.int, Gen.int, Gen.int, Gen.int) { (userId, postId, commentId, replyId) =>
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
          check(Gen.string, Gen.int) { (postValue, postId) =>
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
          check(Gen.string, Gen.int) { (postValue, postId) =>
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
    ),
  )

  case class NewPost(value: String)
  case class PostCreated(id: Int)
}
