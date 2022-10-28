package zio.http.api

import zio.http._
import zio.http.model.headers.values.{ContentBase, ContentDisposition, ContentType}
import zio.test._

object ContentMiddlewareSpec extends ZIOSpecDefault {
  val response      = Response.text("Hello World")
  override def spec =
    suite("ContentMiddlewareSpec")(
      test("add content length header") {
        for {
          response <- api.Middleware
            .withContentLength(11)
            .apply(Http.succeed(response))
            .apply(Request.get(URL.empty))
        } yield assertTrue(response.headers.contentLength.contains(11))
      },
      test("add content type header") {
        for {
          response <- api.Middleware
            .withContentType(ContentType.`text/plain`)
            .apply(Http.succeed(response))
            .apply(Request.get(URL.empty))
        } yield assertTrue(response.headers.contentType.contains(ContentType.`text/plain`.toStringValue))
      },
      test("add content base header") {
        for {
          response <- api.Middleware
            .withContentBase(ContentBase.toContentBase("http://localhost:8080"))
            .apply(Http.succeed(response))
            .apply(Request.get(URL.empty))
        } yield assertTrue(response.headers.contentBase.contains("http://localhost:8080"))
      },
      test("add content disposition header") {
        for {
          response <- api.Middleware
            .withContentDisposition(ContentDisposition.attachment("foo.txt"))
            .apply(Http.succeed(response))
            .apply(Request.get(URL.empty))
        } yield assertTrue(response.headers.contentDisposition.contains("attachment; filename=foo.txt"))
      },
    )
}
