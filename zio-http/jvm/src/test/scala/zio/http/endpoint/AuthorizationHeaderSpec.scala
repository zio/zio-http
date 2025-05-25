package zio.http.endpoint

import zio._
import zio.test._

import zio.http._
import zio.http.codec._
import zio.http.Method._

object AuthorizationHeaderSpec extends ZIOSpecDefault {

  override def spec = suite("AuthorizationHeaderSpec")(
    test("should respond with 401 Unauthorized when required authorization header is missing") {
      // Define an endpoint that requires the Authorization header
      val endpoint =
        Endpoint(GET / "test")
          .header(HeaderCodec.authorization)
          .out[Unit]

      // Implement the endpoint
      val route = endpoint.implement(_ => ZIO.unit)

      // Create a request to /test with no Authorization header
      val request = Request.get(URL(Path.root / "test"))

      // Run the request through the routes and check the response
      for {
        response <- Routes(route).runZIO(request)
      } yield assertTrue(response.status == Status.Unauthorized)
    }
  )
}
