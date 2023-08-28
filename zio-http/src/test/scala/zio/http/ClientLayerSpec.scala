package zio.http

import java.util.concurrent.TimeUnit

import zio.test.Assertion.isLessThan
import zio.test.{TestAspect, assertZIO}
import zio.{Clock, ZIO, durationInt}

import zio.http.Client

object ClientLayerSpec extends ZIOHttpSpec {

  def clientLayerSpec = suite("ClientLayerSpec")(
    test("default client should shutdown within 200 ms") {
      val timeDifference = for {
        startTime <- ZIO.scoped {
          Client.default.build *>
            Clock.currentTime(TimeUnit.MILLISECONDS)
        }
        endTime   <- Clock.currentTime(TimeUnit.MILLISECONDS)
      } yield endTime - startTime
      assertZIO[Any, Throwable, Long](timeDifference)(isLessThan(200L))
    } @@ TestAspect.withLiveClock,
  )

  override def spec = suite("ClientLayer")(clientLayerSpec)

}
