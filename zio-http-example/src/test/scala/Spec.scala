import zio.test._
import zio.test.Assertion.equalTo
import zio.http._

object Spec extends ZIOSpecDefault {

  def spec = suite("http")(
    test("should be ok") {
      val app = Handler.ok.toHttpApp
      val req = Request.get(URL(Root))
      assertZIO(app.runZIO(req))(equalTo(Response.ok))
    }
  )
}