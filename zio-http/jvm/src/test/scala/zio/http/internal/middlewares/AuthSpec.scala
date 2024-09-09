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

import zio.Config.Secret
import zio.test.Assertion._
import zio.test._
import zio.{Ref, ZIO}

import zio.http._
import zio.http.internal.TestExtensions

object AuthSpec extends ZIOHttpSpec with TestExtensions {
  def extractStatus(response: Response): Status = response.status

  private val successBasicHeader: Headers  = Headers(Header.Authorization.Basic("user", "resu"))
  private val failureBasicHeader: Headers  = Headers(Header.Authorization.Basic("user", "user"))
  private val bearerContent: String        = "dummyBearerToken"
  private val bearerToken: Secret          = Secret(bearerContent)
  private val successBearerHeader: Headers = Headers(Header.Authorization.Bearer(bearerToken))
  private val failureBearerHeader: Headers = Headers(Header.Authorization.Bearer(bearerContent + "SomethingElse"))

  private val basicAuthM     = HandlerAspect.basicAuth { c =>
    Secret(c.uname.reverse) == c.upassword
  }
  private val basicAuthZIOM  = HandlerAspect.basicAuthZIO { c =>
    ZIO.succeed(Secret(c.uname.reverse) == c.upassword)
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
        case Header.Authorization.Basic(uname, password) if Secret(uname.reverse) == password =>
          Some(AuthContext(uname))
        case _                                                                                =>
          None
      }

    }
  }

  def spec = suite("AuthSpec")(
    suite("basicAuth")(
      test("Request is accepted if the basic authentication succeeds") {
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
        val app =
          (handler((_: Request) => withContext((c: AuthContext) => Response.text(c.value))) @@ basicAuthContextM).merge
            .mapZIO(_.body.asString)
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = successBasicHeader)))(equalTo("user"))
      },
      test("Extract username via context with Routes") {
        val app = {
          Routes(
            Method.GET / "context" ->
              handler { (_: Request) => withContext((c: AuthContext) => Response.text(c.value))} @@ basicAuthContextM,
          )
        }
        assertZIO(
          app
            .runZIO(Request.get(URL.root / "context").copy(headers = successBasicHeader))
            .flatMap(_.body.asString),
        )(equalTo("user"))
      },
    ),
    suite("basicAuthZIO")(
      test("Request is accepted if the basic authentication succeeds") {
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
      test("Provide for multiple routes") {
        val secureRoutes = Routes(
          Method.GET / "a" -> handler((_: Request) => withContext((ctx: AuthContext) => Response.text(ctx.value))),
          Method.GET / "b" / int("id")      -> handler((id: Int, _: Request) =>
            withContext((ctx: AuthContext) => Response.text(s"for id: $id: ${ctx.value}")),
          ),
          Method.GET / "c" / string("name") -> handler((name: String, _: Request) =>
            withContext((ctx: AuthContext) => Response.text(s"for name: $name: ${ctx.value}")),
          ),
          // Needs version of @@ that removes the context from the environment
        ) @@ basicAuthContextM
        val app          = secureRoutes
        for {
          s1     <- app.runZIO(Request.get(URL(Path.root / "a")).copy(headers = successBasicHeader))
          s1Body <- s1.body.asString
          s2     <- app.runZIO(Request.get(URL(Path.root / "b" / "1")).copy(headers = successBasicHeader))
          s2Body <- s2.body.asString
          s3     <- app.runZIO(Request.get(URL(Path.root / "c" / "name")).copy(headers = successBasicHeader))
          s3Body <- s3.body.asString
          resultStatus = s1.status == Status.Ok && s2.status == Status.Ok && s3.status == Status.Ok
          resultBody   = s1Body == "user" && s2Body == "for id: 1: user" && s3Body == "for name: name: user"
        } yield assertTrue(resultStatus, resultBody)
      },
    ),
    suite("bearerAuth")(
      test("Request is accepted if the bearer authentication succeeds") {
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
        val app1 = Routes(Method.GET / "a" -> Handler.ok)
        val app2 = Routes(Method.GET / "b" -> Handler.ok)
        val app3 = Routes(Method.GET / "c" -> Handler.ok)
        val app  = app1 ++ app2 @@ bearerAuthM ++ app3
        for {
          s1 <- app.runZIO(Request.get(URL(Path.root / "a")).copy(headers = failureBearerHeader))
          s2 <- app.runZIO(Request.get(URL(Path.root / "b")).copy(headers = failureBearerHeader))
          s3 <- app.runZIO(Request.get(URL(Path.root / "c")).copy(headers = failureBearerHeader))
          result = s1.status == Status.Ok && s2.status == Status.Unauthorized && s3.status == Status.Ok
        } yield assertTrue(result)
      },
    ),
    suite("bearerAuthZIO")(
      test("Request is accepted if the bearer authentication succeeds") {
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
        val app1 = Routes(Method.GET / "a" -> Handler.ok)
        val app2 = Routes(Method.GET / "b" -> Handler.ok)
        val app3 = Routes(Method.GET / "c" -> Handler.ok)
        val app  = app1 ++ app2 @@ bearerAuthZIOM ++ app3
        for {
          s1 <- app.runZIO(Request.get(URL(Path.root / "a")).copy(headers = failureBearerHeader))
          s2 <- app.runZIO(Request.get(URL(Path.root / "b")).copy(headers = failureBearerHeader))
          s3 <- app.runZIO(Request.get(URL(Path.root / "c")).copy(headers = failureBearerHeader))
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
