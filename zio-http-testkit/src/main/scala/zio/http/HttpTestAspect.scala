package zio.http

import zio._
import zio.test._

object HttpTestAspect {

  private def withMode(mode: Mode): TestAspectAtLeastR[Scope] =
    TestAspect.aroundWith(
      ZIO.succeed {
        val previous = Mode.current
        java.lang.System.setProperty("zio.http.mode", mode.toString)
        previous
      },
    )((restorePrevious: Mode) => ZIO.succeed(java.lang.System.setProperty("zio.http.mode", restorePrevious.toString)))

  val devMode: TestAspectAtLeastR[Scope] =
    withMode(Mode.Dev)

  val prodMode: TestAspectAtLeastR[Scope] =
    withMode(Mode.Prod)

  val preprodMode: TestAspectAtLeastR[Scope] =
    withMode(Mode.Preprod)

}
