package zio.http.h2

import scala.annotation.experimental

import zio._
import zio.test._

@experimental
object FlowControllerSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("FlowControllerSpec")(
      test("initial window values") {
        val fc = makeFlowController()

        assertTrue(
          fc.connectionWindow == 65535,
          fc.streamWindow(1) == 65535,
        )
      },
      test("consumeSendWindow decrements both windows") {
        val fc = makeFlowController()
        fc.consumeSendWindow(1, 100)

        assertTrue(
          fc.connectionWindow == 65435,
          fc.streamWindow(1) == 65435,
        )
      },
      test("applyWindowUpdate restores connection window") {
        val fc = makeFlowController()
        fc.consumeSendWindow(1, 100)
        fc.applyWindowUpdate(0, 1000)

        assertTrue(
          fc.connectionWindow == 66435,
          fc.streamWindow(1) == 65435,
        )
      },
      test("applyWindowUpdate restores stream window") {
        val fc = makeFlowController()
        fc.consumeSendWindow(1, 100)
        fc.applyWindowUpdate(1, 1000)

        assertTrue(
          fc.connectionWindow == 65435,
          fc.streamWindow(1) == 66435,
        )
      },
      test("registerStream and removeStream control stream visibility") {
        val fc               = new FlowController(initialConnectionWindow = 65535, initialStreamWindow = 65535)
        fc.registerStream(1)
        val registeredWindow = fc.streamWindow(1)
        fc.removeStream(1)

        ZIO.attempt(fc.streamWindow(1)).either.map {
          case Left(_: NoSuchElementException) =>
            assertTrue(registeredWindow == 65535)
          case _                               =>
            assertTrue(false)
        }
      },
      test("window overflow throws FlowControlException") {
        val fc = new FlowController(initialConnectionWindow = Int.MaxValue - 5, initialStreamWindow = 65535)
        fc.registerStream(1)

        ZIO.attempt(fc.applyWindowUpdate(0, 6)).either.map {
          case Left(_: FlowController.FlowControlException) => assertTrue(true)
          case _                                            => assertTrue(false)
        }
      },
      test("consumeSendWindow with zero bytes is a no-op") {
        val fc = makeFlowController()
        fc.consumeSendWindow(1, 0)

        assertTrue(
          fc.connectionWindow == 65535,
          fc.streamWindow(1) == 65535,
        )
      },
    )

  private def makeFlowController(): FlowController = {
    val fc = new FlowController(initialConnectionWindow = 65535, initialStreamWindow = 65535)
    fc.registerStream(1)
    fc
  }
}
