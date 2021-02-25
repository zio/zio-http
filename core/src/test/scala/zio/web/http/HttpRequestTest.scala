package zio.web.http

import zio.test._
import zio.test.Assertion.equalTo
import zio.test.{ DefaultRunnableSpec, ZSpec }

object HttpRequestTest extends DefaultRunnableSpec {

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("Http request test")(
      test("headers with zip") {

        val request = HttpRequest.Zip(
          HttpRequest.Header("content-type").map(_.toUpperCase()),
          HttpRequest.Header("content-length")
        )

        assert(request.headers)(equalTo(List("content-type", "content-length")))
      },
      test("headers with orElseEither") {

        val request = HttpRequest.OrElseEither(
          HttpRequest.Header("content-type"),
          HttpRequest.Header("content-length")
        )

        assert(request.headers)(equalTo(List("content-type", "content-length")))
      },
      test("custom fold with orElseEither") {

        val request =
          HttpRequest.Zip(
            HttpRequest.OrElseEither(
              HttpRequest.Header("content-length"),
              HttpRequest.Header("content-length2")
            ),
            HttpRequest.Header("content-length3")
          )

        val result = request.fold(List.empty[String]) {
          case (headers, HttpRequest.Header(name)) if name != "content-length" =>
            headers ++ List(name)
        }
        assert(result)(equalTo(List("content-length2", "content-length3")))
      }
    )
}
