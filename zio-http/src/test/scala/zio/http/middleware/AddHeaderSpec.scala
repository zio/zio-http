package zio.http.middleware

import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.model.headers.HeaderConstructors
import zio.http.model.{Cookie, Headers, Status}
import zio.test.Assertion._
import zio.test._
import zio.{Ref, ZIO}

object AddHeaderSpec extends ZIOSpecDefault with HttpAppTestExtensions with HeaderConstructors {

  private val acceptHeader     = accept("text/html")
  private val connectionHeader = connection("keep-alive")
  private val response         = Response(headers = age("12"))

  private val appAddingAcceptHeader = Http.ok.withMiddleware(api.Middleware.addHeader(acceptHeader.headers.head))
  private val appAddingHeadersList  =
    Http.ok.withMiddleware(api.Middleware.addHeaders(acceptHeader ++ connectionHeader))
  private val appAddingHeader       =
    Http.response(response).withMiddleware(api.Middleware.addHeaders(acceptHeader ++ connectionHeader))

  private val req = Request.get(URL.empty)

  override def spec = suite("addHeaders")(
    test("Specified header is added to response") {

      for {
        response <- appAddingAcceptHeader(req)
      } yield assert(response.headersAsList)(contains(acceptHeader))
    },
    test("Specified header is added to response with other headers in place") {

      for {
        response <- appAddingHeader(req)
      } yield assert(response.headersAsList)(
        hasSubset(acceptHeader ++ connectionHeader ++ age("12")),
      )
    },
    test("Specified list of headers is added to response") {

      for {
        response <- appAddingHeadersList(req)
      } yield assert(response.headersAsList)(hasSubset(acceptHeader ++ connectionHeader))
    },
  )

}
