package example.testing

import zio._
import zio.http._
import zio.test._

object UnitTestingIndividualHandlers extends ZIOSpecDefault {
  def spec = test("returns user data") {
    val routes = Routes(
      Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
        ZIO.succeed(Response.json(s"""{"id": $id, "name": "User $id"}"""))
      }
    )

    for {
      response <- routes.runZIO(Request.get(URL(Path.root / "users" / "42")))
      body <- response.body.asString
    } yield assertTrue(body.contains("User 42"))
  }
}
