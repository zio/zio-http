package zio.http.api

import zio.http._
import zio.test._

object ExpiresMiddlewareSpec extends ZIOSpecDefault {
  val response      = Response.ok
  override def spec =
    suite("ExpiresMiddlewareSpec")(
      suite("valid values")(
        test("add expires") {
          for {
            response <- api.Middleware
              .withExpires("Wed, 21 Oct 2015 07:28:00 GMT")
              .apply(Handler.succeed(response).toRoute)
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.expires.getOrElse("error").equals("Wed, 21 Oct 2015 07:28:00 GMT"))
        },
      ),
      suite("invalid values")(
        test("add invalid date for expires returns 0") {
          for {
            response <- api.Middleware
              .withExpires("*()!*#&#$^")
              .apply(Handler.succeed(response).toRoute)
              .toZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.expires.getOrElse("error").equals("0"))
        },
      ),
    )
}
