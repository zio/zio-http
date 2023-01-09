package zio.http.api

import zio.http._
import zio.http.model.MediaType
import zio.http.model.headers.values.AccessControlMaxAge
import zio.test._

object AccessControlAllowMaxAgeSpec extends ZIOSpecDefault {
  val response      = Response.ok
  override def spec =
    suite("AccessControlAllowMaxAgeSpec")(
      suite("valid values")(
        test("add allow control access max age") {
          for {
            response <- api.Middleware
              .withAccessControlAllowMaxAge("10")
              .apply(Handler.succeed(response).toRoute)
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.accessControlMaxAge.getOrElse("error").equals("10"))
        },
      ),
      suite("invalid values")(
        test("add invalid allow control access max age returns default 5") {
          for {
            response <- api.Middleware
              .withAccessControlAllowMaxAge("*()!*#&#$^")
              .apply(Handler.succeed(response).toRoute)
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.accessControlMaxAge.getOrElse("error").equals("5"))
        },
        test("add negative allow control access max age returns default 5") {
          for {
            response <- api.Middleware
              .withAccessControlAllowMaxAge("-10")
              .apply(Handler.succeed(response).toRoute)
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.accessControlMaxAge.getOrElse("error").equals("5"))
        },
      ),
    )
}
