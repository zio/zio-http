package zio.http.api

import zio._
import zio.http.api
import zio.http._
import zio.test._

object ContentMiddlewareSpec extends ZIOSpecDefault {
  val response = Response.text("Hello World")
  override def spec =
    suite("ContentMiddlewareSpec"){
      test("should add content length header"){
        for {
          response <- api.Middleware.withContentLength(11).apply(Http.succeed(response))
            .apply(Request.get(URL.empty))
        } yield assertTrue(response.headers.contentLength.contains(11))

      }
    }
}
