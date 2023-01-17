package zio.http.api

import zio.http._
import zio.test._

object TransferEncodingMiddlewareSpec extends ZIOSpecDefault {
  val response      = Response.ok
  override def spec =
    suite("TransferEncodingMiddlewareSpec")(
      suite("valid values")(
        test("add chunked TransferEncoding") {
          for {
            response <- api.Middleware
              .withTransferEncoding("chunked")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.transferEncoding.getOrElse("error").equals("chunked"))
        },
        test("add compress TransferEncoding") {
          for {
            response <- api.Middleware
              .withTransferEncoding("compress")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.transferEncoding.getOrElse("error").equals("compress"))
        },
        test("add deflate TransferEncoding") {
          for {
            response <- api.Middleware
              .withTransferEncoding("deflate")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.transferEncoding.getOrElse("error").equals("deflate"))
        },
        test("add gzip TransferEncoding") {
          for {
            response <- api.Middleware
              .withTransferEncoding("gzip")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.transferEncoding.getOrElse("error").equals("gzip"))
        },
      ),
      suite("invalid values")(
        test("add invalid TransferEncoding") {
          for {
            response <- api.Middleware
              .withTransferEncoding("*grabag$*&()")
              .apply(Handler.succeed(response).toHttp)
              .runZIO(Request.get(URL.empty))
          } yield assertTrue(response.headers.transferEncoding.getOrElse("error").equals(""))
        },
      ),
    )
}
