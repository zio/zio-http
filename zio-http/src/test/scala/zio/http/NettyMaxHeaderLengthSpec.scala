package zio.http

import zio.test._
import zio.{Scope, ZIO}

import zio.http.model._

object NettyMaxHeaderLengthSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    test("should get a failure instead of an empty body") {
      val app = Handler
        .fromFunctionZIO[Request] { request =>
          request.body.asString.map { body =>
            val responseBody = if (body.isEmpty) "<empty>" else body
            Response.text(responseBody)
          } // this should not be run, as the request is invalid
        }
        .toHttp
        .withDefaultErrorResponse
      for {
        port <- Server.install(app)
        url     = s"http://localhost:$port"
        headers = Headers(
          Header.UserAgent.Product("a looooooooooooooooooooooooooooong header", None),
        )

        res  <- Client.request(url, headers = headers, content = Body.fromString("some-body"))
        data <- res.body.asString
      } yield assertTrue(res.status == Status.InternalServerError, data == "")
    }.provide(
      Client.default,
      Server.live,
      ServerConfig.live(ServerConfig(maxHeaderSize = 1)),
    )
}
