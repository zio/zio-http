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

  private val basicAuthM: RequestHandlerMiddleware[Nothing, Any, Nothing, Any]     = HttpAppMiddleware.basicAuth { c =>
    c.uname.reverse == c.upassword
  }
  private val basicAuthZIOM: RequestHandlerMiddleware[Nothing, Any, Nothing, Any]  = HttpAppMiddleware.basicAuthZIO {
    c =>
      ZIO.succeed(c.uname.reverse == c.upassword)
  }
  private val bearerAuthM: RequestHandlerMiddleware[Nothing, Any, Nothing, Any]    = HttpAppMiddleware.bearerAuth { c =>
    c == bearerToken
  }
  private val bearerAuthZIOM: RequestHandlerMiddleware[Nothing, Any, Nothing, Any] = HttpAppMiddleware.bearerAuthZIO {
    c =>
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
        val app1 = Http.collectHandler[Request] { case Method.GET -> Root / "a" =>
          Handler.ok
        }
        val app2 = Http.collectHandler[Request] { case Method.GET -> Root / "b" =>
          Handler.ok
        }
        val app3 = Http.collectHandler[Request] { case Method.GET -> Root / "c" =>
          Handler.ok
        }
        val app  = (app1 ++ app2 @@ bearerAuthM ++ app3).status
        for {
          s1 <- app.runZIO(Request.get(URL(Root / "a")).copy(headers = failureBearerHeader))
          s2 <- app.runZIO(Request.get(URL(Root / "b")).copy(headers = failureBearerHeader))
          s3 <- app.runZIO(Request.get(URL(Root / "c")).copy(headers = failureBearerHeader))
        } yield assertTrue(
          s1 == Status.Ok && s2 == Status.Unauthorized && s3 == Status.Ok,
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
        val app1 = Http.collectHandler[Request] { case Method.GET -> Root / "a" =>
          Handler.ok
        }
        val app2 = Http.collectHandler[Request] { case Method.GET -> Root / "b" =>
          Handler.ok
        }
        val app3 = Http.collectHandler[Request] { case Method.GET -> Root / "c" =>
          Handler.ok
        }
        val app  = (app1 ++ app2 @@ bearerAuthZIOM ++ app3).status
        for {
          s1 <- app.runZIO(Request.get(URL(Root / "a")).copy(headers = failureBearerHeader))
          s2 <- app.runZIO(Request.get(URL(Root / "b")).copy(headers = failureBearerHeader))
          s3 <- app.runZIO(Request.get(URL(Root / "c")).copy(headers = failureBearerHeader))
        } yield assertTrue(
          s1 == Status.Ok && s2 == Status.Unauthorized && s3 == Status.Ok,
        )
      },
    ),
    suite("custom")(
      test("Providing context from auth middleware") {
        def auth[R0] = RequestHandlerMiddlewares.customAuthProviding[R0, AuthContext]((request: Request) =>
          request.header(Header.Authorization).map(auth => AuthContext(auth.renderedValue.toString)),
        )

        val app1 = Handler.text("ok") @@ auth[Any]
        val app2 = Handler.fromZIO {
          for {
            base <- ZIO.service[BaseService]
            auth <- ZIO.service[AuthContext]
          } yield Response.text(s"${base.value} ${auth.value}")
        } @@ auth[BaseService]

        for {
          r1     <- app1.runZIO(Request.get(URL.empty))
          r2     <- app1.runZIO(Request.get(URL.empty).copy(headers = Headers(Header.Authorization.Bearer("auth"))))
          r2body <- r2.body.asString
          r3     <- app2.runZIO(Request.get(URL.empty))
          r4     <- app2.runZIO(Request.get(URL.empty).copy(headers = Headers(Header.Authorization.Bearer("auth"))))
          r4body <- r4.body.asString
        } yield assertTrue(
          extractStatus(r1) == Status.Unauthorized,
          extractStatus(r2) == Status.Ok,
          r2body == "ok",
          extractStatus(r3) == Status.Unauthorized,
          extractStatus(r4) == Status.Ok,
          r4body == "base Bearer auth",
        )
      }.provideLayer(ZLayer.succeed(BaseService("base"))),
      test("Providing context from auth middleware effectfully") {
        def auth[R0] = RequestHandlerMiddlewares.customAuthProvidingZIO[R0, UserService, Throwable, AuthContext](
          (request: Request) =>
            request.header(Header.Authorization) match {
              case Some(Header.Authorization.Bearer(value)) if value.startsWith("_") =>
                ZIO.service[UserService].map { usvc => Some(AuthContext(usvc.prefix + value)) }
              case Some(value)                                                       =>
                ZIO.fail(new RuntimeException(s"Invalid auth header $value"))
              case None                                                              =>
                ZIO.none
            },
        )

        val app1 = Handler.text("ok") @@ auth[Any]
        val app2 = Handler.fromZIO {
          for {
            base <- ZIO.service[BaseService]
            auth <- ZIO.service[AuthContext]
          } yield Response.text(s"${base.value} ${auth.value}")
        } @@ auth[BaseService]

        for {
          r1 <- app1.runZIO(Request.get(URL.empty))
          r2 <- app1.runZIO(Request.get(URL.empty).copy(headers = Headers(Header.Authorization.Bearer("auth")))).exit
          r3 <- app1.runZIO(Request.get(URL.empty).copy(headers = Headers(Header.Authorization.Bearer("_auth"))))
          r3body <- r3.body.asString
          r4     <- app2.runZIO(Request.get(URL.empty))
          r5 <- app2.runZIO(Request.get(URL.empty).copy(headers = Headers(Header.Authorization.Bearer("auth")))).exit
          r6 <- app2.runZIO(Request.get(URL.empty).copy(headers = Headers(Header.Authorization.Bearer("_auth"))))
          r6body <- r6.body.asString
        } yield assertTrue(
          extractStatus(r1) == Status.Unauthorized,
          r2.isFailure,
          extractStatus(r3) == Status.Ok,
          r3body == "ok",
          extractStatus(r4) == Status.Unauthorized,
          r5.isFailure,
          extractStatus(r6) == Status.Ok,
          r6body == "base user_auth",
        )
      }.provide(ZLayer.succeed(BaseService("base")), ZLayer.succeed(UserService("user"))),
      test("Contextual auth evaluation per request") {
        def auth =
          RequestHandlerMiddlewares.customAuthProvidingZIO[Any, CounterService, Throwable, AuthContext](_ =>
            for {
              counter <- ZIO.service[CounterService].map(_.counter)
              _       <- counter.update(_ + 1)
              value   <- counter.get
            } yield Some(AuthContext(value.toString)),
          )

        def httpEndpoint(str: String) = Http.collect[Request] { case Method.GET -> Root / `str` =>
          Response.ok
        }

        val httpApi = (httpEndpoint("1") ++ httpEndpoint("2")) @@ auth
        for {
          r1      <- httpApi.runZIO(Request.get(URL(Root / "1")))
          counter <- ZIO.service[CounterService].flatMap(_.counter.get)
        } yield assertTrue(extractStatus(r1) == Status.Ok, counter == 1)
      }.provide(ZLayer.fromZIO(Ref.make(0).map(CounterService.apply))),
    ),
  )

  final case class UserService(prefix: String)
  final case class BaseService(value: String)
  final case class AuthContext(value: String)
  final case class CounterService(counter: Ref[Int])
}
