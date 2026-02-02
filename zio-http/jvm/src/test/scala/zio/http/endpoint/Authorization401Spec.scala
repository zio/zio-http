package zio.http.endpoint

import zio.test._
import zio.http._
import zio.http.codec._

object Authorization401Spec extends ZIOSpecDefault {

  override def spec =
    suite("Authorization401Spec")(
      test("should return 401 Unauthorized for missing Authorization header") {
        val endpoint = Endpoint(Method.GET / "test")
          .header(HeaderCodec.authorization)
          .out[Unit]
        val route    = endpoint.implementPurely(_ => ())
        val request  = Request(method = Method.GET, url = url"/test")
        for {
          response <- route.toRoutes.runZIO(request)
        } yield assertTrue(response.status == Status.Unauthorized)
      },
      test("should return 401 Unauthorized for malformed Authorization header") {
        val endpoint = Endpoint(Method.GET / "test")
          .header(HeaderCodec.authorization)
          .out[Unit]
        val route    = endpoint.implementPurely(_ => ())
        val request  =
          Request(method = Method.GET, url = url"/test").addHeader(Header.Authorization.Basic("user", "pass"))
        // If it fails to decode, it should also be 401.
        for {
          response <- route.toRoutes.runZIO(request)
        } yield assertTrue(response.status == Status.Unauthorized)
      },
    )
}
