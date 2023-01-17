package zio.http.api

import zio.http._
import zio.http.api.Middleware.cors
import zio.http.internal.HttpAppTestExtensions
import zio.http.middleware.Cors.CorsConfig
import zio.http.model.{Headers, Method, Status}
import zio.test.Assertion.hasSubset
import zio.test._

object CorsMiddlewareSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  val app = Handler.ok.toHttp.withMiddleware(cors(CorsConfig(allowedMethods = Some(Set(Method.GET)))))

  override def spec =
    suite("CorsMiddlewares")(
      test("OPTIONS request") {
        val request = Request
          .options(URL(!! / "success"))
          .copy(
            headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("https://domain-a:8080"),
          )

        val initialHeaders = Headers
          .accessControlAllowCredentials(true)
          .withAccessControlAllowMethods(Method.GET)
          .withAccessControlAllowOrigin("https://domain-a:8080")

        val expected = CorsConfig().allowedHeaders
          .fold(Headers.empty) { header =>
            header
              .map(value => Headers.empty.withAccessControlAllowHeaders(value))
              .fold(initialHeaders)(_ ++ _)
          }
          .toList

        for {
          res <- app.runZIO(request)
        } yield assert(res.headersAsList)(hasSubset(expected)) &&
          assertTrue(res.status == Status.NoContent)
      },
      test("GET request") {
        val request =
          Request
            .get(URL(!! / "success"))
            .copy(
              headers = Headers.accessControlRequestMethod(Method.GET) ++ Headers.origin("https://domain-a:8080"),
            )

        val expected = Headers
          .accessControlExposeHeaders("*")
          .withAccessControlAllowOrigin("https://domain-a:8080")
          .withAccessControlAllowMethods(Method.GET)
          .withAccessControlAllowCredentials(true)
          .toList

        for {
          res <- app.runZIO(request)
        } yield assert(res.headersAsList)(hasSubset(expected))
      },
    )
}
