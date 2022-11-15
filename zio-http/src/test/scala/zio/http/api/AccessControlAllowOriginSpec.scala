package zio.http.api

import zio.http._
import zio.http.model.MediaType
import zio.http.model.headers.values.AccessControlAllowOrigin
import zio.test._

object AccessControlAllowOriginSpec extends ZIOSpecDefault {
  val response      = Response.ok
  override def spec =
    suite("AccessControlAllowOriginSpec")(
      suite("valid values")(
        test("add * allow control access origin") {
          for {
            response <- api.Middleware
              .withAccessControlAllowOrigin("*")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.accessControlAllowOrigin.contains("*"))
        },
        test("add null allow control access origin") {
          for {
            response <- api.Middleware
              .withAccessControlAllowOrigin("null")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.accessControlAllowOrigin.contains("null"))
        },
        test("add url allow control access origin") {
          for {
            response <- api.Middleware
              .withAccessControlAllowOrigin("http://localhost:8080")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.accessControlAllowOrigin.contains("http://localhost:8080"))
        },
      ),
      suite("invalid values")(
        test("add invalid value allow control access origin") {
          for {
            response <- api.Middleware
              .withAccessControlAllowOrigin("*()!*#&#$^")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.accessControlAllowOrigin.contains(""))
        },
      ),
    )
}
