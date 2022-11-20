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
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.retryAfter.getOrElse("error").equals("Wed, 21 Oct 2015 07:28:00 GMT"))
        },
        test("add duration RetryAfter") {
          for {
            response <- api.Middleware
              .withRetryAfter("10")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.retryAfter.getOrElse("error").equals("10"))
        },
      ),
      suite("invalid values")(
        test("add invalid RetryAfter") {
          for {
            response <- api.Middleware
              .withRetryAfter("garbage!@##$$")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.retryAfter.getOrElse("error").equals("0"))
        },
<<<<<<< HEAD
=======
        test("add invalid negative duration RetryAfter") {
          for {
            response <- api.Middleware
              .withRetryAfter("-10")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.retryAfter.getOrElse("error").equals("0"))
        },
>>>>>>> 9f0bcecc (Commit code for retry after middleware.)
      ),
    )
}
