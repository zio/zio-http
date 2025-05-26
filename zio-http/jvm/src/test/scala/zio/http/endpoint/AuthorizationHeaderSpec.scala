package zio.http.endpoint

import zio._
import zio.test._

import zio.http._
import zio.http.codec._
import zio.http.Method._

object AuthorizationHeaderSpec extends ZIOSpecDefault {
  override def spec =
    suite("AuthorizationHeaderSpec")(
      test("should respond with 401 Unauthorized when required authorization header is missing") {
        val endpoint = Endpoint(Method.GET / "test")
          .header(HeaderCodec.authorization)
          .out[Unit]
        val route    = endpoint.implement(_ => ZIO.unit)
        val request  =
          Request(method = Method.GET, url = url"/test")
        for {
          response <- route.toRoutes.runZIO(request)
        } yield assertTrue(Status.Unauthorized == response.status)
      },
    )
}
