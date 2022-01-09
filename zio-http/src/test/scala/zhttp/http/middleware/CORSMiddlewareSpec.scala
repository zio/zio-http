package zhttp.http.middleware

import zhttp.http._
import zhttp.http.middleware.Middleware.cors
import zhttp.internal.HttpAppTestExtensions
import zio.test.Assertion.hasSubset
import zio.test._

object CORSMiddlewareSpec extends DefaultRunnableSpec with HttpAppTestExtensions {
  override def spec = suite("CORSMiddleware") {
    // FIXME:The test should ideally pass with `Http.ok` also
    val app = Http.collect[Request] { case Method.GET -> !! / "success" => Response.ok } @@ cors()
    testM("OPTIONS request") {
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
          CORS.DefaultCORSConfig.allowedHeaders.getOrElse(Set.empty).mkString(","),
        )
        .toList

      for {
        res <- app(request)
      } yield assert(res.getHeadersAsList)(hasSubset(expected)) &&
        assertTrue(res.status == Status.NO_CONTENT)
    } +
      testM("GET request") {
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
