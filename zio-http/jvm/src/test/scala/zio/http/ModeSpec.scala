package zio.http

import zio.test._

object ModeSpec extends ZIOSpecDefault {
  override def spec = suite("ModeSpec")(
    test("Mode should be Test") {
      assertTrue(Mode.Prod.isActive)
    },
  )
}
