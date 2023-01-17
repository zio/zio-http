package zio.http.api

import zio.http._
import zio.test._

object RetryAfterMiddlewareSpec extends ZIOSpecDefault {
  val response      = Response.ok
  override def spec =
    suite("RetryAfterMiddlewareSpec")(
      suite("valid values")(
        test("add date RetryAfter") {
          for {
            response <- api.Middleware
              .withRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.retryAfter.getOrElse("error").equals("Wed, 21 Oct 2015 07:28:00 GMT"))
        },
        test("add duration RetryAfter") {
          for {
            response <- api.Middleware
              .withRetryAfter("10")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.retryAfter.getOrElse("error").equals("10"))
        },
      ),
      suite("invalid values")(
        test("add invalid RetryAfter") {
          for {
            response <- api.Middleware
              .withRetryAfter("garbage!@##$$")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.retryAfter.getOrElse("error").equals("0"))
        },
        test("add invalid negative duration RetryAfter") {
          for {
            response <- api.Middleware
              .withRetryAfter("-10")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.retryAfter.getOrElse("error").equals("0"))
        },
      ),
    )
}
