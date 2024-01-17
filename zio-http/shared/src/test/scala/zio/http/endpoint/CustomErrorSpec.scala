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
import zio.http.endpoint.EndpointSpec.extractStatus
import zio.http.forms.Fixtures.formField

object CustomErrorSpec extends ZIOHttpSpec {
  def spec = suite("CustomErrorSpec")(
    test("simple custom error response") {
      check(Gen.int, Gen.int) { (userId, customCode) =>
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
      check(Gen.int(Int.MinValue, 1000), Gen.int(1001, Int.MaxValue)) { (myUserId, invalidUserId) =>
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
  )

  sealed trait TestError
  object TestError {
    final case class InvalidUser(userId: Int)         extends TestError
    final case class UnexpectedError(message: String) extends TestError

    implicit val invalidUserSchema: Schema[TestError.InvalidUser]         = DeriveSchema.gen[TestError.InvalidUser]
    implicit val unexpectedErrorSchema: Schema[TestError.UnexpectedError] = DeriveSchema.gen[TestError.UnexpectedError]
  }

  case class User(
    @validate(Validation.greaterThan(0))
    id: Int,
  )
}
