
package zio.http

import zio._
import zio.http.endpoint._
import zio.test._

object HandlerAspectSpec extends ZIOSpecDefault {
  def spec = suite("HandlerAspectSpec")(
    test("should handle HandlerAspect with path parameters correctly") {
      val myEndpoint = Endpoint.get("test" / int("id"))
        .out[String]
        .handle { id => ZIO.succeed(s"Received $id") }

      val myAspect = HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { req =>
        ZIO.succeed(req)
      })

      val app = myEndpoint @@ myAspect toHttpApp

      for {
        response <- app.runZIO(Request.get(URL.decode("/test/123").toOption.get))
      } yield assertTrue(response.status == Status.Ok) &&
        assertTrue(response.body.asString == "\"Received 123\"")
    },
    test("should handle HandlerAspect without path parameters correctly") {
      val handler = Handler.fromFunctionZIO[Request] { req =>
        ZIO.succeed(Response.text("OK"))
      }

      val myAspect = HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { req =>
        ZIO.succeed(req)
      })

      val app = handler @@ myAspect toHttpApp

      for {
        response <- app.runZIO(Request.get(URL.decode("/").toOption.get))
      } yield assertTrue(response.status == Status.Ok) &&
        assertTrue(response.body.asString == "\"OK\"")
    }
  )
}
