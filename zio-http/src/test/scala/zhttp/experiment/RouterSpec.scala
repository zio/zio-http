package zhttp.experiment

import zhttp.experiment.Router._
import zhttp.http.Method
import zio.test.Assertion._
import zio.test._

object RouterSpec extends DefaultRunnableSpec {
  def spec = suite("Router")(
    test("Method")(assert(OnlyMethod(Method.GET))(isSubtype[Router[Unit]])),
  )
}
