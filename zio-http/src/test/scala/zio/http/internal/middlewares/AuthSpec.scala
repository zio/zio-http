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

import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.test.Assertion._
import zio.test._
import zio.{Ref, ZIO}

object AuthSpec extends ZIOHttpSpec with HttpAppTestExtensions {
  def extractStatus(response: Response): Status = response.status

  private val successBasicHeader: Headers  = Headers(Header.Authorization.Basic("user", "resu"))
  private val failureBasicHeader: Headers  = Headers(Header.Authorization.Basic("user", "user"))
  private val bearerToken: String          = "dummyBearerToken"
  private val successBearerHeader: Headers = Headers(Header.Authorization.Bearer(bearerToken))
  private val failureBearerHeader: Headers = Headers(Header.Authorization.Bearer(bearerToken + "SomethingElse"))

  private val basicAuthM     = HandlerAspect.basicAuth { c =>
    c.uname.reverse == c.upassword
  }
  private val basicAuthZIOM  = HandlerAspect.basicAuthZIO { c =>
    ZIO.succeed(c.uname.reverse == c.upassword)
  }
  private val bearerAuthM    = HandlerAspect.bearerAuth { c =>
    c == bearerToken
  }
  private val bearerAuthZIOM = HandlerAspect.bearerAuthZIO { c =>
    ZIO.succeed(c == bearerToken)
  }

  private val basicAuthContextM = HandlerAspect.customAuthProviding[AuthContext] { r =>
    {
      r.headers.get(Header.Authorization).flatMap {
        case Header.Authorization.Basic(uname, password) if uname.reverse == password =>
          Some(AuthContext(uname))
        case _                                                                        =>
          None
      }

    }
  }

  def spec = suite("AuthSpec")(
    suite("basicAuth")(
      test("HttpApp is accepted if the basic authentication succeeds") {
        val app = (Handler.ok @@ basicAuthM).merge.status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = successBasicHeader)))(equalTo(Status.Ok))
      },
      test("Uses forbidden app if the basic authentication fails") {
        val app = (Handler.ok @@ basicAuthM).merge.status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBasicHeader)))(equalTo(Status.Unauthorized))
      },
      test("Responses should have WWW-Authentication header if Basic Auth failed") {
        val app = (Handler.ok @@ basicAuthM).merge.header(Header.WWWAuthenticate)
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBasicHeader)))(isSome)
      },
      test("Extract username via context") {
        val app = (Handler.fromFunction[(AuthContext, Request)] { case (c, _) =>
          Response.text(c.value)
        } @@ basicAuthContextM).merge.mapZIO(_.body.asString)
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = successBasicHeader)))(equalTo("user"))
      },
      test("Extract username via context with Routes") {
        val app = {
          Routes(
            Method.GET / "context" -> basicAuthContextM ->
              Handler.fromFunction[(AuthContext, Request)] { case (c: AuthContext, _) => Response.text(c.value) }
          )
        }.toHttpApp
        assertZIO(
          app
            .runZIO(Request.get(URL.root / "context").copy(headers = successBasicHeader))
            .flatMap(_.body.asString)
        )(equalTo("user"))
      },
    ),
    suite("basicAuthZIO")(
      test("HttpApp is accepted if the basic authentication succeeds") {
        val app = (Handler.ok @@ basicAuthZIOM).merge.status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = successBasicHeader)))(equalTo(Status.Ok))
      },
      test("Uses forbidden app if the basic authentication fails") {
        val app = (Handler.ok @@ basicAuthZIOM).merge.status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBasicHeader)))(equalTo(Status.Unauthorized))
      },
      test("Responses should have WWW-Authentication header if Basic Auth failed") {
        val app = (Handler.ok @@ basicAuthZIOM).merge.header(Header.WWWAuthenticate)
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBasicHeader)))(isSome)
      },
    ),
    suite("bearerAuth")(
      test("HttpApp is accepted if the bearer authentication succeeds") {
        val app = (Handler.ok @@ bearerAuthM).merge.status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = successBearerHeader)))(equalTo(Status.Ok))
      },
      test("Uses forbidden app if the bearer authentication fails") {
        val app = (Handler.ok @@ bearerAuthM).merge.status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBearerHeader)))(equalTo(Status.Unauthorized))
      },
      test("Responses should have WWW-Authentication header if bearer Auth failed") {
        val app = (Handler.ok @@ bearerAuthM).merge.header(Header.WWWAuthenticate)
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBearerHeader)))(isSome)
      },
      test("Does not affect fallback apps") {
        val app1 = Routes(Method.GET / "a" -> Handler.ok).toHttpApp
        val app2 = Routes(Method.GET / "b" -> Handler.ok).toHttpApp
        val app3 = Routes(Method.GET / "c" -> Handler.ok).toHttpApp
        val app  = app1 ++ app2 @@ bearerAuthM ++ app3
        for {
          s1 <- app.runZIO(Request.get(URL(Root / "a")).copy(headers = failureBearerHeader))
          s2 <- app.runZIO(Request.get(URL(Root / "b")).copy(headers = failureBearerHeader))
          s3 <- app.runZIO(Request.get(URL(Root / "c")).copy(headers = failureBearerHeader))
          result = s1.status == Status.Ok && s2.status == Status.Unauthorized && s3.status == Status.Ok
        } yield assertTrue(result)
      },
    ),
    suite("bearerAuthZIO")(
      test("HttpApp is accepted if the bearer authentication succeeds") {
        val app = (Handler.ok @@ bearerAuthZIOM).merge.status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = successBearerHeader)))(equalTo(Status.Ok))
      },
      test("Uses forbidden app if the bearer authentication fails") {
        val app = (Handler.ok @@ bearerAuthZIOM).merge.status
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBearerHeader)))(equalTo(Status.Unauthorized))
      },
      test("Responses should have WWW-Authentication header if bearer Auth failed") {
        val app = (Handler.ok @@ bearerAuthZIOM).merge.header(Header.WWWAuthenticate)
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBearerHeader)))(isSome)
      },
      test("Does not affect fallback apps") {
        val app1 = Routes(Method.GET / "a" -> Handler.ok).toHttpApp
        val app2 = Routes(Method.GET / "b" -> Handler.ok).toHttpApp
        val app3 = Routes(Method.GET / "c" -> Handler.ok).toHttpApp
        val app  = app1 ++ app2 @@ bearerAuthZIOM ++ app3
        for {
          s1 <- app.runZIO(Request.get(URL(Root / "a")).copy(headers = failureBearerHeader))
          s2 <- app.runZIO(Request.get(URL(Root / "b")).copy(headers = failureBearerHeader))
          s3 <- app.runZIO(Request.get(URL(Root / "c")).copy(headers = failureBearerHeader))
          result = s1.status == Status.Ok && s2.status == Status.Unauthorized && s3.status == Status.Ok
        } yield assertTrue(result)
      },
    ),
  )

  final case class UserService(prefix: String)
  final case class BaseService(value: String)
  final case class AuthContext(value: String)
  final case class CounterService(counter: Ref[Int])
}
