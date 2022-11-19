package zio.http.api

import zio.http._
import zio.test._

object ConnectionMiddlewareSpec extends ZIOSpecDefault {
  val response      = Response.ok
  override def spec =
    suite("ConnectionMiddlewareSpec")(
      suite("valid values")(
        test("add connection close") {
          for {
            response <- api.Middleware
              .withConnection("close")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.connection.getOrElse("error").equals("close"))
        },
        test("add connection keep-alive") {
          for {
            response <- api.Middleware
              .withConnection("keep-alive")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.connection.getOrElse("error").equals("keep-alive"))
        },
      ),
      suite("invalid values")(
        test("add invalid connection return '' ") {
          for {
            response <- api.Middleware
              .withConnection("*()!*#&#$^")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.connection.getOrElse("error").equals(""))
        },
      ),
    )
}
