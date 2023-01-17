package zio.http.api

import zio.http._
import zio.test._

object RefererMiddlewareSpec extends ZIOSpecDefault {
  val response      = Response.ok
  override def spec =
    suite("RefererMiddlewareSpec")(
      suite("valid values")(
        test("add valid url to RefererMiddlewareSpec") {
          for {
            response <- api.Middleware
              .withReferer("https://developer.mozilla.org/en-US/docs/Web/JavaScript")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.referer
              .getOrElse("error")
              .equals("https://developer.mozilla.org/en-US/docs/Web/JavaScript"),
          )
        },
        test("add shorter valid url to RefererMiddlewareSpec") {
          for {
            response <- api.Middleware
              .withReferer("https://developer.mozilla.org/en-US/")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(
            response.headers.referer
              .getOrElse("error")
              .equals("https://developer.mozilla.org/en-US/"),
          )
        },
      ),
      suite("invalid values")(
        test("add invalid value to RefererMiddlewareSpec") {
          for {
            response <- api.Middleware
              .withReferer("developer.mozilla.org/en-US/")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.referer.getOrElse("error").equals(""))
        },
        test("add invalid garbage value to RefererMiddlewareSpec") {
          for {
            response <- api.Middleware
              .withReferer("garbage)(*&^%")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.referer.getOrElse("error").equals(""))
        },
      ),
    )
}
