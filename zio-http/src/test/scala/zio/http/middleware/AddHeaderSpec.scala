package zio.http.middleware

import zio.Ref
import zio.http._
import zio.http.internal.HttpAppTestExtensions
import zio.http.model.{Cookie, Headers, Status}
import zio.test.Assertion._
import zio.test._
import zio.ZIO

object AddHeaderSpec extends ZIOSpecDefault with HttpAppTestExtensions {
  
  private val acceptHeader = Headers.Header("Accept","text/html")
  private val connectionHeader = Headers.Header("Connection","keep-alive")

  private val appSingleHeader = Http.ok.withMiddleware(api.Middleware.addHeader(acceptHeader))
  private val appHeadersList = Http.ok.withMiddleware(api.Middleware.addHeaders(Headers(acceptHeader,connectionHeader)))
  private val req = Request.get(URL.empty)

  override def spec = suite("addHeaders")(
    test("Specified header is added") {

      for {
        response <- appSingleHeader(req)
        _ <- ZIO.succeed(println(response.headersAsList))
      } yield assert(response.headersAsList)(contains(acceptHeader))
    },
        test("Specified headers is added") {

      for {
        response <- appHeadersList(req)
        _ <- ZIO.succeed(println(response.headersAsList))
      } yield assert(response.headersAsList)(hasSubset(Headers(acceptHeader,connectionHeader)))
    },
  )

 



}
