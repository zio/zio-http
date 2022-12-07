package zio.http.api

import zio.http._
import zio.test._

object IfRangeMiddlewareSpec extends ZIOSpecDefault {
  val response      = Response.ok
  override def spec =
    suite("IfRangeMiddlewareSpec")(
      suite("valid values")(
        test("add valid IfRange") {
          for {
            response <- api.Middleware
              .withIfRange("Wed, 21 Oct 2015 07:28:00 GMT")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.ifRange.getOrElse("error").equals("Wed, 21 Oct 2015 07:28:00 GMT"))
        },
        test("add valid etage IfRange") {
          for {
            response <- api.Middleware
              .withIfRange(""""675af34563dc-tr34"""")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.ifRange.getOrElse("error").equals(""""675af34563dc-tr34""""))
        },
      ),
      suite("invalid values")(
        test("add invalid weak etag IfRange") {
          for {
            response <- api.Middleware
              .withIfRange("W/675af34563dc-tr34")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.ifRange.getOrElse("error").equals(""))
        },
        test("add invalid IfRange") {
          for {
            response <- api.Middleware
              .withIfRange("*&^%$#@")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.ifRange.getOrElse("error").equals(""))
        },
      ),
    )
}
