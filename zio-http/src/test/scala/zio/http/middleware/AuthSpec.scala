package zio.http.middleware

import zio.test.Assertion._
import zio.test._
import zio.{ZIO, ZLayer}

import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.model.{HeaderNames, Headers, Method, Status}

object AuthSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  private val successBasicHeader: Headers  = Headers.basicAuthorizationHeader("user", "resu")
  private val failureBasicHeader: Headers  = Headers.basicAuthorizationHeader("user", "user")
  private val bearerToken: String          = "dummyBearerToken"
  private val successBearerHeader: Headers = Headers.bearerAuthorizationHeader(bearerToken)
  private val failureBearerHeader: Headers = Headers.bearerAuthorizationHeader(bearerToken + "SomethingElse")

  private val basicAuthM: RequestHandlerMiddleware[Any, Nothing]     = Middleware.basicAuth { c =>
    c.uname.reverse == c.upassword
  }
  private val basicAuthZIOM: RequestHandlerMiddleware[Any, Nothing]  = Middleware.basicAuthZIO { c =>
    ZIO.succeed(c.uname.reverse == c.upassword)
  }
  private val bearerAuthM: RequestHandlerMiddleware[Any, Nothing]    = Middleware.bearerAuth { c =>
    c == bearerToken
  }
  private val bearerAuthZIOM: RequestHandlerMiddleware[Any, Nothing] = Middleware.bearerAuthZIO { c =>
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
        val app = Handler.ok @@ basicAuthM header "WWW-AUTHENTICATE"
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
        val app = Handler.ok @@ basicAuthZIOM header "WWW-AUTHENTICATE"
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
        val app = Handler.ok @@ bearerAuthM header "WWW-AUTHENTICATE"
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBearerHeader)))(isSome)
      },
      test("Does not affect fallback apps") {
        val app1 = Http.collectHandler[Request] { case Method.GET -> !! / "a" =>
          Handler.ok
        }
        val app2 = Http.collectHandler[Request] { case Method.GET -> !! / "b" =>
          Handler.ok
        }
        val app3 = Http.collectHandler[Request] { case Method.GET -> !! / "c" =>
          Handler.ok
        }
        val app  = (app1 ++ app2 @@ bearerAuthM ++ app3).status
        for {
          s1 <- app.runZIO(Request.get(URL(!! / "a")).copy(headers = failureBearerHeader))
          s2 <- app.runZIO(Request.get(URL(!! / "b")).copy(headers = failureBearerHeader))
          s3 <- app.runZIO(Request.get(URL(!! / "c")).copy(headers = failureBearerHeader))
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
        val app = Handler.ok @@ bearerAuthZIOM header "WWW-AUTHENTICATE"
        assertZIO(app.runZIO(Request.get(URL.empty).copy(headers = failureBearerHeader)))(isSome)
      },
      test("Does not affect fallback apps") {
        val app1 = Http.collectHandler[Request] { case Method.GET -> !! / "a" =>
          Handler.ok
        }
        val app2 = Http.collectHandler[Request] { case Method.GET -> !! / "b" =>
          Handler.ok
        }
        val app3 = Http.collectHandler[Request] { case Method.GET -> !! / "c" =>
          Handler.ok
        }
        val app  = (app1 ++ app2 @@ bearerAuthZIOM ++ app3).status
        for {
          s1 <- app.runZIO(Request.get(URL(!! / "a")).copy(headers = failureBearerHeader))
          s2 <- app.runZIO(Request.get(URL(!! / "b")).copy(headers = failureBearerHeader))
          s3 <- app.runZIO(Request.get(URL(!! / "c")).copy(headers = failureBearerHeader))
        } yield assertTrue(
          s1 == Status.Ok && s2 == Status.Unauthorized && s3 == Status.Ok,
        )
      },
    ),
    suite("custom")(
      test("Providing context from auth middleware") {
        def auth[R0] = RequestHandlerMiddlewares.customAuthProviding[R0, AuthContext]((headers: Headers) =>
          headers.get(HeaderNames.authorization).map(AuthContext),
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
          r2     <- app1.runZIO(Request.get(URL.empty).copy(headers = Headers.authorization("auth")))
          r2body <- r2.body.asString
          r3     <- app2.runZIO(Request.get(URL.empty))
          r4     <- app2.runZIO(Request.get(URL.empty).copy(headers = Headers.authorization("auth")))
          r4body <- r4.body.asString
        } yield assertTrue(
          r1.status == Status.Unauthorized,
          r2.status == Status.Ok,
          r2body == "ok",
          r3.status == Status.Unauthorized,
          r4.status == Status.Ok,
          r4body == "base auth",
        )
      }.provideLayer(ZLayer.succeed(BaseService("base"))),
    ),
  )

  final case class BaseService(value: String)
  final case class AuthContext(value: String)
}
