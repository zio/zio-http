package zio.http.middleware

import zio.http.Middleware.cors
import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.middleware.Cors.CorsConfig
import zio.test.Assertion.hasSubset
import zio.test._

object CorsSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  val app = Http.ok @@ cors(CorsConfig(allowedMethods = Some(Set(Method.GET))))

  override def spec = suite("CorsMiddlewares")(
    test("OPTIONS request") {
      val request = Request(
        method = Method.OPTIONS,
        url = URL(!! / "success"),
        headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("test-env"),
      )

      val initialHeaders = Headers
        .accessControlAllowCredentials(true)
        .withAccessControlAllowMethods(Method.GET)
        .withAccessControlAllowOrigin("test-env")

      val expected = CorsConfig().allowedHeaders
        .fold(Headers.empty) { h =>
          h
            .map(value => Headers.empty.withAccessControlAllowHeaders(value))
            .fold(initialHeaders)(_ ++ _)
        }
        .toList
      for {
        res <- app(request)
      } yield assert(res.headersAsList)(hasSubset(expected)) &&
        assertTrue(res.status == Status.NoContent)

    },
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
      } yield assert(res.headersAsList)(hasSubset(expected))
    },
  )
}
