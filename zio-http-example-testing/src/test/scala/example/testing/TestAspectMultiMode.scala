package example.testing

import zio._
import zio.http._
import zio.test._

object TestAspectMultiMode extends ZIOSpecDefault {
  def spec = suite("Multi-Mode Testing")(
    test("dev mode allows verbose output") {
      assertTrue(Mode.isDev)
    } @@ HttpTestAspect.devMode,

    test("preprod mode is intermediate") {
      assertTrue(Mode.isPreprod)
    } @@ HttpTestAspect.preprodMode,

    test("prod mode is strict") {
      assertTrue(Mode.isProd)
    } @@ HttpTestAspect.prodMode,
  ) @@ TestAspect.sequential
}
