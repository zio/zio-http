package zio.http

import zio._
import zio.test._

/**
 * Test aspects that run a test with a fixed value of the `zio.http.mode` system
 * property. Each aspect sets the property before the test and sets it back to
 * the previous [[Mode]] after the test.
 */
object HttpTestAspect {

  private def withMode(mode: Mode): TestAspectAtLeastR[Scope] =
    TestAspect.aroundWith(
      ZIO.succeed {
        val previous = Mode.current
        java.lang.System.setProperty("zio.http.mode", mode.toString)
        previous
      },
    )((restorePrevious: Mode) => ZIO.succeed(java.lang.System.setProperty("zio.http.mode", restorePrevious.toString)))

  /** A test aspect that runs the test with [[Mode.Dev]] as the current mode. */
  val devMode: TestAspectAtLeastR[Scope] =
    withMode(Mode.Dev)

  /** A test aspect that runs the test with [[Mode.Prod]] as the current mode. */
  val prodMode: TestAspectAtLeastR[Scope] =
    withMode(Mode.Prod)

  /** A test aspect that runs the test with [[Mode.Preprod]] as the current mode. */
  val preprodMode: TestAspectAtLeastR[Scope] =
    withMode(Mode.Preprod)

}
