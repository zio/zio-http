package example.testing

import zio._
import zio.http._
import zio.test._

object ModeBasicSetup extends ZIOSpecDefault {
  def spec = suite("Mode Testing")(
    test("development mode enables verbose logging") {
      for {
        currentMode <- ZIO.succeed(Mode.current)
      } yield assertTrue(currentMode == Mode.Dev)
    } @@ HttpTestAspect.devMode,

    test("production mode disables verbose logging") {
      for {
        currentMode <- ZIO.succeed(Mode.current)
      } yield assertTrue(currentMode == Mode.Prod)
    } @@ HttpTestAspect.prodMode,

    test("modes are isolated between tests") {
      for {
        modeInProd <- ZIO.succeed(Mode.current)
      } yield assertTrue(modeInProd == Mode.Prod)
    } @@ HttpTestAspect.prodMode,
  )
}
