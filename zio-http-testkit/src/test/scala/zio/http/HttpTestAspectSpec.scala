package zio.http

import zio.test._

object HttpTestAspectSpec extends ZIOSpecDefault {
  def spec = suite("HttpTestAspectSpec")(
    test("Preprod is enabled vai test aspect") {
      assertTrue(Mode.current == Mode.Preprod)
    } @@ HttpTestAspect.preprodMode,
    test("Prod is enabled via test aspect") {
      assertTrue(Mode.current == Mode.Prod)
    } @@ HttpTestAspect.prodMode,
    test("Dev is enabled via test aspect") {
      assertTrue(Mode.current == Mode.Dev)
    } @@ HttpTestAspect.devMode,
  ) @@ TestAspect.sequential
}
