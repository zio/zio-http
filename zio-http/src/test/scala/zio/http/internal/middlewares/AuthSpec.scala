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

package zio.http.internal.middlewares

import zio.test.Assertion._
import zio.test._
import zio.{Ref, ZIO, ZLayer}

import zio.http._
import zio.http.internal.HttpAppTestExtensions

object AuthSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  def extractStatus(response: Response): Status = response.status

  private val successBasicHeader: Headers  = Headers(Header.Authorization.Basic("user", "resu"))
  private val failureBasicHeader: Headers  = Headers(Header.Authorization.Basic("user", "user"))
  private val bearerToken: String          = "dummyBearerToken"
  private val successBearerHeader: Headers = Headers(Header.Authorization.Bearer(bearerToken))
  private val failureBearerHeader: Headers = Headers(Header.Authorization.Bearer(bearerToken + "SomethingElse"))

  private val basicAuthM: Middleware[Any, Unit]     = Middleware.basicAuth { c =>
    c.uname.reverse == c.upassword
  }
  private val basicAuthZIOM: Middleware[Any, Unit]  = Middleware.basicAuthZIO { c =>
    ZIO.succeed(c.uname.reverse == c.upassword)
  }
  private val bearerAuthM: Middleware[Any, Unit]    = Middleware.bearerAuth { c =>
    c == bearerToken
  }
  private val bearerAuthZIOM: Middleware[Any, Unit] = Middleware.bearerAuthZIO { c =>
    ZIO.succeed(c == bearerToken)
  }

  def spec = suite("AuthSpec")(
    suite("basicAuth")(
      test("HttpApp is accepted if the basic authentication succeeds") {
        val app = (Handler.ok @@ basicAuthM).status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = successBasicHeader)))(equalTo(Status.Ok))
      },
      test("Uses forbidden app if the basic authentication fails") {
        val app = (Handler.ok @@ basicAuthM).status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBasicHeader)))(equalTo(Status.Unauthorized))
      },
      test("Responses should have WWW-Authentication header if Basic Auth failed") {
        val app = (Handler.ok @@ basicAuthM).header(Header.WWWAuthenticate)
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBasicHeader)))(isSome)
      },
    ),
    suite("basicAuthZIO")(
      test("HttpApp is accepted if the basic authentication succeeds") {
        val app = (Handler.ok @@ basicAuthZIOM).status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = successBasicHeader)))(equalTo(Status.Ok))
      },
      test("Uses forbidden app if the basic authentication fails") {
        val app = (Handler.ok @@ basicAuthZIOM).status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBasicHeader)))(equalTo(Status.Unauthorized))
      },
      test("Responses should have WWW-Authentication header if Basic Auth failed") {
        val app = (Handler.ok @@ basicAuthZIOM).header(Header.WWWAuthenticate)
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBasicHeader)))(isSome)
      },
    ),
    suite("bearerAuth")(
      test("HttpApp is accepted if the bearer authentication succeeds") {
        val app = (Handler.ok @@ bearerAuthM).status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = successBearerHeader)))(equalTo(Status.Ok))
      },
      test("Uses forbidden app if the bearer authentication fails") {
        val app = (Handler.ok @@ bearerAuthM).status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBearerHeader)))(equalTo(Status.Unauthorized))
      },
      test("Responses should have WWW-Authentication header if bearer Auth failed") {
        val app = (Handler.ok @@ bearerAuthM).header(Header.WWWAuthenticate)
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBearerHeader)))(isSome)
      },
      test("Does not affect fallback apps") {
        val app1 = Routes(Method.GET / "a" -> Handler.ok).toApp
        val app2 = Routes(Method.GET / "b" -> Handler.ok).toApp
        val app3 = Routes(Method.GET / "c" -> Handler.ok).toApp
        val app  = app1 ++ app2 @@ bearerAuthM ++ app3
        for {
          s1 <- app.runZIO(Request.get(URL(Root / "a")).copy(headers = failureBearerHeader))
          s2 <- app.runZIO(Request.get(URL(Root / "b")).copy(headers = failureBearerHeader))
          s3 <- app.runZIO(Request.get(URL(Root / "c")).copy(headers = failureBearerHeader))
        } yield assertTrue(
          s1.status == Status.Ok && s2.status == Status.Unauthorized && s3.status == Status.Ok,
        )
      },
    ),
    suite("bearerAuthZIO")(
      test("HttpApp is accepted if the bearer authentication succeeds") {
        val app = (Handler.ok @@ bearerAuthZIOM).status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = successBearerHeader)))(equalTo(Status.Ok))
      },
      test("Uses forbidden app if the bearer authentication fails") {
        val app = (Handler.ok @@ bearerAuthZIOM).status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBearerHeader)))(equalTo(Status.Unauthorized))
      },
      test("Responses should have WWW-Authentication header if bearer Auth failed") {
        val app = (Handler.ok @@ bearerAuthZIOM).header(Header.WWWAuthenticate)
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBearerHeader)))(isSome)
      },
      test("Does not affect fallback apps") {
        val app1 = Routes(Method.GET / "a" -> Handler.ok).toApp
        val app2 = Routes(Method.GET / "b" -> Handler.ok).toApp
        val app3 = Routes(Method.GET / "c" -> Handler.ok).toApp
        val app  = app1 ++ app2 @@ bearerAuthZIOM ++ app3
        for {
          s1 <- app.runZIO(Request.get(URL(Root / "a")).copy(headers = failureBearerHeader))
          s2 <- app.runZIO(Request.get(URL(Root / "b")).copy(headers = failureBearerHeader))
          s3 <- app.runZIO(Request.get(URL(Root / "c")).copy(headers = failureBearerHeader))
        } yield assertTrue(
          s1.status == Status.Ok && s2.status == Status.Unauthorized && s3.status == Status.Ok,
        )
      },
    ),
  )

  final case class UserService(prefix: String)
  final case class BaseService(value: String)
  final case class AuthContext(value: String)
  final case class CounterService(counter: Ref[Int])
}
