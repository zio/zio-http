package zio.http.endpoint

import zio.Config.Secret
import zio.test._
import zio.{Scope, ZIO}

import zio.http._
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

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AuthSpec")(
      test("Auth with context") {
        val endpoint = Endpoint(Method.GET / "test").out[String](MediaType.text.`plain`)
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
    )

}
