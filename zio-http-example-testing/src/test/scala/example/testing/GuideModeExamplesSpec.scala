package example.testing

import zio.test._
import zio.http._

/**
 * Dev/Preprod/Prod Modes — Testing Mode-Dependent Behavior
 *
 * Demonstrates how to test mode-dependent behavior using HttpTestAspect
 * to override the current mode for specific tests.
 *
 * Run with: sbt "zio-http-example-testing/testOnly example.testing.GuideModeExamplesSpec"
 */
object GuideModeExamplesSpec extends ZIOSpecDefault {
  def spec = suite("GuideModeExamplesSpec")(
    test("enables preprod logic") {
      assertTrue(Mode.current == Mode.Preprod)
    } @@ HttpTestAspect.preprodMode,

    test("enables prod logic") {
      assertTrue(Mode.isProd)
    } @@ HttpTestAspect.prodMode,
  ) @@ TestAspect.sequential // IMPORTANT: sequential to avoid mode race conditions
}
