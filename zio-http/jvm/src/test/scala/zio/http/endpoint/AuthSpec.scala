package zio.http.endpoint

import zio.Config.Secret
import zio.test.TestAspect.flaky
import zio.test._
import zio.{Scope, ZIO, durationInt}

import zio.http._
import zio.http.codec.{CodecConfig, HttpCodec}
import zio.http.internal.middlewares.AuthSpec.AuthContext

object AuthSpec extends ZIOSpecDefault {

  private val basicAuthContext = HandlerAspect.customAuthProviding[AuthContext] { r =>
    {
      r.headers.get(Header.Authorization).flatMap {
        case Header.Authorization.Basic(uname, password) if Secret(uname.reverse) == password =>
          Some(AuthContext(uname))
        case _                                                                                =>
          None
      }

    }
  }

  private val basicOrBearerAuthContext = HandlerAspect.customAuthProviding[AuthContext] { r =>
    {
      r.headers.get(Header.Authorization).flatMap {
        case Header.Authorization.Basic(uname, password) if Secret(uname.reverse) == password =>
          Some(AuthContext(uname))
        case Header.Authorization.Bearer(token) if token == Secret("admin")                   =>
          Some(AuthContext("bearer-admin"))
        case _                                                                                =>
          None
      }

    }
  }

  private val queryParamAuthContext = HandlerAspect.customAuthProviding[AuthContext] { r =>
    r.queryParam("token").filter(_ == "admin").map(AuthContext.apply)
  }

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AuthSpec")(
      test("Auth with context") {
        val endpoint = Endpoint(Method.GET / "test").out[String](MediaType.text.`plain`).auth(AuthType.Basic)
        val routes   =
          Routes(
            endpoint.implementHandler(handler((_: Unit) => withContext((ctx: AuthContext) => ctx.value))),
          ) @@ basicAuthContext
        val response = routes.run(
          Request(
            method = Method.GET,
            url = url"/test",
            headers = Headers(
              Header.Authorization.Basic("admin", "admin".reverse),
              Header.Accept(MediaType.text.`plain`),
            ),
          ),
        )
        for {
          response <- response
          body     <- response.body.asString
        } yield assertTrue(body == "admin")
      },
      test("Auth basic or bearer with context") {
        val endpoint      =
          Endpoint(Method.GET / "test").out[String](MediaType.text.`plain`).auth(AuthType.Basic | AuthType.Bearer)
        val routes        =
          Routes(
            endpoint.implementHandler(handler((_: Unit) => withContext((ctx: AuthContext) => ctx.value))),
          ) @@ basicOrBearerAuthContext
        val responseBasic = routes.run(
          Request(
            method = Method.GET,
            url = url"/test",
            headers = Headers(
              Header.Authorization.Basic("admin", "admin".reverse),
              Header.Accept(MediaType.text.`plain`),
            ),
          ),
        )

        val responseBearer = routes.run(
          Request(
            method = Method.GET,
            url = url"/test",
            headers = Headers(
              Header.Authorization
                .Bearer("admin"),
              Header.Accept(MediaType.text.`plain`),
            ),
          ),
        )
        for {
          responseBasic <- responseBasic
          bodyBasic     <- responseBasic.body.asString
          statusBasic = responseBasic.status
          responseBearer <- responseBearer
          bodyBearer     <- responseBearer.body.asString
          statusBearer = responseBearer.status
        } yield assertTrue(
          statusBasic.isSuccess,
          bodyBasic == "admin",
          statusBearer.isSuccess,
          bodyBearer == "bearer-admin",
        )
      },
      test("Auth from query parameter") {
        val endpoint = Endpoint(Method.GET / "test")
          .out[String](MediaType.text.`plain`)
          .auth(AuthType.Custom(HttpCodec.query[String]("token")))
        val routes   =
          Routes(
            endpoint.implementHandler(handler((_: Unit) => withContext((ctx: AuthContext) => ctx.value))),
          ) @@ queryParamAuthContext
        val response = routes.run(
          Request(
            method = Method.GET,
            url = url"/test?token=admin",
            headers = Headers(
              Header.Accept(MediaType.text.`plain`),
            ),
          ),
        )
        for {
          response <- response
          body     <- response.body.asString
        } yield assertTrue(body == "admin")
      },
      suite("With server")(
        test("Auth with context and endpoint client") {
          val endpoint = Endpoint(Method.GET / "test").out[String](MediaType.text.`plain`).auth(AuthType.Basic)
          val routes   =
            Routes(
              endpoint.implementHandler(handler((_: Unit) => withContext((ctx: AuthContext) => ctx.value))),
            ) @@ basicAuthContext

          val response = for {
            client <- ZIO.service[Client]
            locator    = EndpointLocator.fromURL(url"http://localhost:8080")
            executor   = EndpointExecutor(client, locator, Header.Authorization.Basic("admin", "admin".reverse))
            invocation = endpoint(())
            response <- ZIO.scoped(executor(invocation))
          } yield response

          for {
            _        <- Server
              .serve(routes.handleErrorCauseZIO(c => ZIO.logInfoCause("yes!", c).as(Response.text(""))))
              .forkDaemon
              .catchAllCause(c => ZIO.logInfoCause(c)) <* ZIO.sleep(1.seconds)
            response <- response
          } yield assertTrue(response == "admin")
        } @@ flaky,
        test("Auth basic or bearer with context and endpoint client") {
          val endpoint =
            Endpoint(Method.GET / "multiAuth")
              .out[String](MediaType.text.`plain`)
              .auth(AuthType.Basic | AuthType.Bearer)
          val routes   =
            Routes(
              endpoint.implementHandler(handler((_: Unit) => withContext((ctx: AuthContext) => ctx.value))),
            ) @@ basicOrBearerAuthContext

          val responseBasic = for {
            client <- ZIO.service[Client]
            locator    = EndpointLocator.fromURL(url"http://localhost:8080")
            executor   = EndpointExecutor(client, locator, Left(Header.Authorization.Basic("admin", "admin".reverse)))
            invocation = endpoint(())
            response <- ZIO.scoped(executor(invocation))
          } yield response

          val responseBearer = for {
            client <- ZIO.service[Client]
            locator    = EndpointLocator.fromURL(url"http://localhost:8080")
            executor   = EndpointExecutor(client, locator, Right(Header.Authorization.Bearer("admin")))
            invocation = endpoint(())
            response <- ZIO.scoped(executor(invocation))
          } yield response

          for {
            _              <- Server
              .serve(routes.handleErrorCauseZIO(c => ZIO.logInfoCause("yes!", c).as(Response.text(""))))
              .forkDaemon
              .catchAllCause(c => ZIO.logInfoCause(c)) <* ZIO.sleep(1.seconds)
            responseBasic  <- responseBasic
            responseBearer <- responseBearer
          } yield assertTrue(responseBasic == "admin" && responseBearer == "bearer-admin")
        },
        test("Auth from query parameter with context and endpoint client") {
          val endpoint = Endpoint(Method.GET / "query")
            .out[String](MediaType.text.`plain`)
            .auth(AuthType.Custom(HttpCodec.query[String]("token")))
          val routes   =
            Routes(
              endpoint.implementHandler(handler((_: Unit) => withContext((ctx: AuthContext) => ctx.value))),
            ) @@ queryParamAuthContext

          val response = for {
            client <- ZIO.service[Client]
            locator    = EndpointLocator.fromURL(url"http://localhost:8080")
            executor   = EndpointExecutor(client, locator, "admin")
            invocation = endpoint(())
            response <- ZIO.scoped(executor(invocation))
          } yield response

          for {
            _        <- Server
              .serve(routes.handleErrorCauseZIO(c => ZIO.logInfoCause("yes!", c).as(Response.text(""))))
              .forkDaemon
              .catchAllCause(c => ZIO.logInfoCause(c)) <* ZIO.sleep(1.seconds)
            response <- response
          } yield assertTrue(response == "admin")
        } @@ TestAspect.flaky,
        test("Auth with context and endpoint client with path parameter") {
          val endpoint =
            Endpoint(Method.GET / int("a")).out[String](MediaType.text.`plain`).auth(AuthType.Basic)
          val routes   =
            Routes(
              endpoint.implementHandler(handler((_: Int) => withContext((ctx: AuthContext) => ctx.value))),
            ) @@ basicAuthContext

          val response = for {
            client <- ZIO.service[Client]
            locator    = EndpointLocator.fromURL(url"http://localhost:8080")
            executor   = EndpointExecutor(client, locator, Header.Authorization.Basic("admin", "admin".reverse))
            invocation = endpoint(1)
            response <- ZIO.scoped(executor(invocation))
          } yield response

          for {
            _        <- Server
              .serve(routes.handleErrorCauseZIO(c => ZIO.logInfoCause("yes!", c).as(Response.text(""))))
              .forkDaemon
              .catchAllCause(c => ZIO.logInfoCause(c)) <* ZIO.sleep(1.seconds)
            response <- response
          } yield assertTrue(response == "admin")
        },
      ).provideShared(Client.default, Server.default) @@ TestAspect.withLiveClock,
      test("Require Basic Auth, but get Bearer Auth") {
        val endpoint = Endpoint(Method.GET / "test").out[String](MediaType.text.`plain`).auth(AuthType.Basic)
        val routes   =
          Routes(
            endpoint.implementHandler(handler((_: Unit) => "Response")),
          )
        val response = routes.run(
          Request(
            method = Method.GET,
            url = url"/test",
            headers = Headers(
              Header.Authorization.Bearer("admin"),
              Header.Accept(MediaType.text.`plain`),
            ),
          ),
        )
        for {
          response <- response
          status = response.status
        } yield assertTrue(status == Status.Unauthorized)
      },
    )

}
