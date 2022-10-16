package zio.http

import zio._
import zio.http.model.Status
import zio.test._

object TestClientSpec extends ZIOSpecDefault {
  def spec =
    suite("TestClient")(
      suite("Happy Paths")(
        test("addRequestResponse"){
          val request = Request.get(URL.root)
          val request2 = Request.get(URL(Path.decode("users")))
          for {
            _               <- TestClient.addRequestResponse(request, Response.ok)
            goodResponse <- Client.request(request)
            badResponse <- Client.request(request2)
            _               <- TestClient.addRequestResponse(request2, Response.ok)
            goodResponse2 <- Client.request(request)
            badResponse2 <- Client.request(request2)
          } yield assertTrue(goodResponse.status == Status.Ok) && assertTrue(badResponse.status == Status.NotFound) &&
                  assertTrue(goodResponse2.status == Status.Ok) && assertTrue(badResponse2.status == Status.Ok)
        },
        test("addHandler")(
          for {
            _               <- TestClient.addHandler(request => ZIO.succeed(Response.ok))
            response <- Client.request(Request.get(URL.root))
          } yield assertTrue(response.status == Status.Ok),
        ),
      ),
      suite("sad paths")(
        test("error when submitting a request to a blank TestServer")(
          for {
            response <- Client.request(Request.get(URL.root))
          } yield assertTrue(response.status == Status.NotFound),
        )
      )
    ).provide(TestClient.layer)

}
