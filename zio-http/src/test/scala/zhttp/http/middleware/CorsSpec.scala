package zhttp.http.middleware

import zhttp.http.Middleware.cors
import zhttp.http._
import zhttp.http.middleware.Cors.CorsConfig
import zhttp.internal.HttpAppTestExtensions
import zio.test.Assertion.hasSubset
import zio.test._

object CorsSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  override def spec = suite("CorsMiddlewares") {
    val app = Http.ok @@ cors()
    test("OPTIONS request") {
      val request = Request(
        method = Method.OPTIONS,
        url = URL(!! / "success"),
        headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("test-env"),
      )

      val expected = Headers
        .accessControlAllowCredentials(true)
        .withAccessControlAllowMethods(Method.GET)
        .withAccessControlAllowOrigin("test-env")
        .withAccessControlAllowHeaders(
          CorsConfig().allowedHeaders.getOrElse(Set.empty).mkString(","),
        )
        .toList

      for {
        res <- app(request)
      } yield assert(res.getHeadersAsList)(hasSubset(expected)) &&
        assertTrue(res.status == Status.NO_CONTENT)
    } +
      test("GET request") {
        val request =
          Request(
            method = Method.GET,
            url = URL(!! / "success"),
            headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("test-env"),
          )

        val expected = Headers
          .accessControlExposeHeaders("*")
          .withAccessControlAllowOrigin("test-env")
          .withAccessControlAllowMethods(Method.GET)
          .withAccessControlAllowCredentials(true)
          .toList

        for {
          res <- app(request)
        } yield assert(res.getHeadersAsList)(hasSubset(expected))
      }
  }
}
