package zio.http.api

import zio.http._
import zio.http.model.Method._
import zio.test._

object AccessControlAllowMethodsSpec extends ZIOSpecDefault {
  val response = Response.ok

  override def spec =
    suite("withAccessControlAllowMethods")(
      test("add control access to which methods are allowed") {
        for {
          response <- api.Middleware
            .withAccessControlAllowMethods(GET, PUT, POST)
            .apply(Http.succeed(response))
            .apply(Request.get(URL.empty))
        } yield assertTrue(response.headers.accessControlAllowMethods.getOrElse("error").equals("GET, PUT, POST"))
      },
    )
}
