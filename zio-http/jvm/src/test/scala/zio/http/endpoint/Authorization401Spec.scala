package zio.http.endpoint

import zio.test._

import zio.http._
import zio.http.codec.HeaderCodec

object Authorization401Spec extends ZIOHttpSpec {

  override def spec =
    suite("Authorization 401 Spec")(
      test("Missing Authorization header with Bearer auth returns 401 Unauthorized") {
        val endpoint = Endpoint(Method.GET / "secure")
          .out[String]
          .auth(AuthType.Bearer)
        val route    = endpoint.implementHandler(handler((_: Unit) => "ok"))
        val request  = Request(method = Method.GET, url = url"/secure")
        for {
          response <- route.toRoutes.runZIO(request)
        } yield assertTrue(
          response.status == Status.Unauthorized,
          response.header(Header.WWWAuthenticate).isDefined,
        )
      },
      test("Missing Authorization header with Basic auth returns 401 Unauthorized") {
        val endpoint = Endpoint(Method.GET / "secure")
          .out[String]
          .auth(AuthType.Basic)
        val route    = endpoint.implementHandler(handler((_: Unit) => "ok"))
        val request  = Request(method = Method.GET, url = url"/secure")
        for {
          response <- route.toRoutes.runZIO(request)
        } yield assertTrue(
          response.status == Status.Unauthorized,
          response.header(Header.WWWAuthenticate).isDefined,
        )
      },
      test("Malformed Authorization header returns 401 Unauthorized") {
        val endpoint = Endpoint(Method.GET / "secure")
          .out[String]
          .auth(AuthType.Bearer)
        val route    = endpoint.implementHandler(handler((_: Unit) => "ok"))
        val request  = Request(
          method = Method.GET,
          url = url"/secure",
          headers = Headers("authorization", "InvalidScheme foobar"),
        )
        for {
          response <- route.toRoutes.runZIO(request)
        } yield assertTrue(
          response.status == Status.Unauthorized,
        )
      },
      test("Missing non-auth header still returns 400 Bad Request") {
        val endpoint = Endpoint(Method.GET / "test")
          .header(HeaderCodec.accept)
          .out[String]
        val route    = endpoint.implementHandler(handler((_: Header.Accept) => "ok"))
        val request  = Request(method = Method.GET, url = url"/test")
        for {
          response <- route.toRoutes.runZIO(request)
        } yield assertTrue(
          response.status == Status.BadRequest,
        )
      },
      test("Missing Authorization header via HeaderCodec.authorization returns 401") {
        val endpoint = Endpoint(Method.GET / "secure")
          .header(HeaderCodec.authorization)
          .out[String]
        val route    = endpoint.implementHandler(handler((_: Header.Authorization) => "ok"))
        val request  = Request(method = Method.GET, url = url"/secure")
        for {
          response <- route.toRoutes.runZIO(request)
        } yield assertTrue(
          response.status == Status.Unauthorized,
        )
      },
    )
}
