package zhttp.http

import zhttp.internal.HttpGen
import zio.test.Assertion._
import zio.test._

object StatusSpec extends DefaultRunnableSpec {
  private val statusGen = HttpGen.status

  def spec = suite("Status")(
    toAppSpec,
    toResponseSpec,
  )

  def toResponseSpec =
    suite("toResponse")(
      test("status") {
        checkAll(statusGen) { case status =>
          assert(status.toResponse.status)(equalTo(status))
        }
      },
    )

  def toAppSpec = {
    suite("toApp")(
      test("status") {
        checkAll(statusGen) { case status =>
          val res = status.toApp(Request())
          assertM(res.map(_.status))(equalTo(status))
        }
      },
    )
  }
}
