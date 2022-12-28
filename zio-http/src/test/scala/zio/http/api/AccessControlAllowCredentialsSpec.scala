package zio.http.api

import zio.http._
import zio.test._

object AccessControlAllowCredentialsSpec extends ZIOSpecDefault {
  val response = Response.ok

  override def spec =
    suite("withAccessControlAllowCredentials")(
      test("add control access to allow credentials") {
        for {
          response <- api.Middleware
            .withAccessControlAllowCredentials(true)
            .apply(Http.succeed(response))
            .toZIO(Request.get(URL.empty))
        } yield assertTrue(response.headers.accessControlAllowCredentials.equals(Some(true)))
      },
    )
}
