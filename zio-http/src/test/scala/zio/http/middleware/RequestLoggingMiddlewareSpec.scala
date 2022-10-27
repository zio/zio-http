package zio.http.middlewares

// import zio.Ref
import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.model.{Cookie, Headers, Status}
import zio.test.Assertion.equalTo
import zio.test._

object RequestLoggingMiddlewareSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  private val app      = Http.ok.withMiddleware(api.Middleware.requestLogging()).status
  private val setAgent = Headers.userAgent("smith")
  override def spec    = suite("Request Logging Middleware")(
    test("see if 'logging' works") {
      assertZIO(app(Request.get(URL.empty).copy(headers = setAgent)))(equalTo(Status.Ok))
    },
  )

}
