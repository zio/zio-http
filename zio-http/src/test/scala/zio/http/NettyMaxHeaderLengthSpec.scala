package zio.http

import zio.Scope
import zio.test._

import zio.http.model._

object NettyMaxHeaderLengthSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    test("should get a failure instead of an empty body") {
      for {
        port <- Server.install(
          Http
            .collectZIO[Request] { request =>
              request.body.asString.map { body =>
                val responseBody = if (body.isEmpty) "<empty>" else body
                Response.text(responseBody)
              } // this should not be run, as the request is invalid
            }
            .withDefaultErrorResponse,
        )
        url = s"http://localhost:$port"
        headers = Headers(
          Header.UserAgent.Product("a looooooooooooooooooooooooooooong header", None),
        )

        res  <- Client.request(url, headers = headers, content = Body.fromString("some-body"))
        data <- res.body.asString
      } yield {
        assertTrue(res.status == Status.InternalServerError && data == "")
      }
    }.provide(
      Client.default,
      Server.live,
      ServerConfig.live(ServerConfig(maxHeaderSize = 1)),
    )
}
