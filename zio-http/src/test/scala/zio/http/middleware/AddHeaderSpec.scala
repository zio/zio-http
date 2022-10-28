package zio.http.middleware

import zio.Ref
import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.model.{Cookie, Headers, Status}
import zio.test.Assertion._
import zio.test._
import zio.ZIO

object AddHeaderSpec extends ZIOSpecDefault with HttpAppTestExtensions {

  private val acceptHeader     = Headers.Header("Accept", "text/html")
  private val connectionHeader = Headers.Header("Connection", "keep-alive")

  private val appAddingAcceptHeader = Http.ok.withMiddleware(api.Middleware.addHeader(acceptHeader))
  private val appAddingHeadersList  =
    Http.ok.withMiddleware(api.Middleware.addHeaders(Headers(acceptHeader, connectionHeader)))

  private val req = Request.get(URL.empty)

  override def spec = suite("addHeaders")(
    test("Specified header is added to empty request") {

      for {
        response <- appAddingAcceptHeader(req)
      } yield assert(response.headersAsList)(contains(acceptHeader))
    },
    test("Specified header is added to request with other headers in place") {

      for {
        response <- appAddingAcceptHeader(req.copy(headers = Headers(headers = connectionHeader)))
      } yield assert(response.headersAsList)(
        hasSameElements(Headers(acceptHeader, connectionHeader)),
      )
    },
    test("Specified list of headers is added") {

      for {
        response <- appAddingHeadersList(req)
      } yield assert(response.headersAsList)(hasSubset(Headers(acceptHeader, connectionHeader)))
    },
  )

}
