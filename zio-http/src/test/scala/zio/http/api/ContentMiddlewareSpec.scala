package zio.http.api

import zio.http._
import zio.http.model.MediaType
import zio.http.model.headers.values.{ContentDisposition, ContentRange, ContentType}
import zio.test._

object ContentMiddlewareSpec extends ZIOSpecDefault {
  val response      = Response.ok
  override def spec =
    suite("ContentMiddlewareSpec")(
      suite("valid values")(
        test("add content base header") {
          for {
            response <- api.Middleware
              .withContentBase("http://localhost:8080")
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
        test("add content encoding header") {
          for {
            response <- api.Middleware
              .withContentEncoding("gzip")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentEncoding.contains("gzip"))
        },
        test("add content language header") {
          for {
            response <- api.Middleware
              .withContentLanguage("en")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentLanguage.contains("en"))
        },
        test("add content length header") {
          for {
            response <- api.Middleware
              .withContentLength(10)
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentLength.contains(10))
        },
        test("add content location header") {
          for {
            response <- api.Middleware
              .withContentLocation("http://localhost:8080")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentLocation.contains("http://localhost:8080"))
        },
        test("add content md5 header") {
          for {
            response <- api.Middleware
              .withContentMd5("86e3edf31dfc82bcf1d45345f0f63b60")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentMd5.contains("86e3edf31dfc82bcf1d45345f0f63b60"))
        },
        test("add content range header") {
          for {
            response <- api.Middleware
              .withContentRange(ContentRange.toContentRange("bytes 0-10/100"))
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentRange.contains("bytes 0-10/100"))
        },
        test("add content security policy header") {
          for {
            response <- api.Middleware
              .withContentSecurityPolicy("upgrade-insecure-requests")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentSecurityPolicy.contains("upgrade-insecure-requests"))
        },
        test("add content transfer encoding header") {
          for {
            response <- api.Middleware
              .withContentTransferEncoding("7bit")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentTransferEncoding.contains("7bit"))
        },
        test("add content type header") {
          for {
            response <- api.Middleware
              .withContentType(ContentType.fromMediaType(MediaType.application.`json`))
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentType.contains("application/json"))
        },
      ),
      suite("Invalid values")(
        test("add content base header") {
          for {
            response <- api.Middleware
              .withContentBase("foo")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentBase.contains(""))
        },
        test("add content disposition header") {
          for {
            response <- api.Middleware
              .withContentDisposition(ContentDisposition.attachment("foo.txt"))
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentDisposition.contains("attachment; filename=foo.txt"))
        },
        test("add content encoding header") {
          for {
            response <- api.Middleware
              .withContentEncoding("foo")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentEncoding.contains(""))
        },
        test("add content language header") {
          for {
            response <- api.Middleware
              .withContentLanguage("foo")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentLanguage.contains(""))
        },
        test("add content length header") {
          for {
            response <- api.Middleware
              .withContentLength(-1)
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentLength.isEmpty)
        },
        test("add content location header") {
          for {
            response <- api.Middleware
              .withContentLocation("##_-foo")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentLocation.contains(""))
        },
        test("add content md5 header") {
          for {
            response <- api.Middleware
              .withContentMd5("foo")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentMd5.contains(""))
        },
        test("add content range header") {
          for {
            response <- api.Middleware
              .withContentRange(ContentRange.toContentRange("foo"))
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentRange.contains(""))
        },
        test("add content security policy header") {
          for {
            response <- api.Middleware
              .withContentSecurityPolicy("foo")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentSecurityPolicy.contains(""))
        },
        test("add content transfer encoding header") {
          for {
            response <- api.Middleware
              .withContentTransferEncoding("foo")
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentTransferEncoding.contains(""))
        },
        test("add content type header") {
          for {
            response <- api.Middleware
              .withContentType(ContentType.fromMediaType(MediaType.application.`json`))
              .apply(Http.succeed(response))
              .apply(Request.get(URL.empty))
          } yield assertTrue(response.headers.contentType.contains("application/json"))
        },
      ),
    )
}
