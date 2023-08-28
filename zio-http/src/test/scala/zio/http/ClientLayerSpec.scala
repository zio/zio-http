package zio.http

import java.util.concurrent.TimeUnit
import zio.test.Assertion.{isGreaterThan, isLessThan, isWithin}
import zio.test.{TestAspect, assertZIO}
import zio.{Clock, ZIO, ZLayer, durationInt}
import zio.http.Client
import zio.http.netty.NettyConfig

object ClientLayerSpec extends ZIOHttpSpec {

  def clientLayerSpec = suite("ClientLayerSpec")(
    test("default client should shutdown within 250 ms") {
      val timeDifference = for {
        startTime <- ZIO.scoped {
          Client.default.build *>
            Clock.currentTime(TimeUnit.MILLISECONDS)
        }
        endTime   <- Clock.currentTime(TimeUnit.MILLISECONDS)
      } yield endTime - startTime
      assertZIO[Any, Throwable, Long](timeDifference)(isWithin(50L, 250L))
    } @@ TestAspect.withLiveClock,
    test("netty client should allow customizing quiet period for client shutdown") {
      val customNettyConfig =
        NettyConfig.default
                   .copy(shutdownQuietPeriodDuration = 2900.millis, shutdownTimeoutDuration = 3100.millis)
      val customClientLayer =
        (ZLayer.succeed(Client.Config.default) ++ ZLayer.succeed(customNettyConfig) ++
          DnsResolver.default) >>> Client.live

      val timeDifference = for {
        startTime <- ZIO.scoped {
          customClientLayer.build *> Clock.currentTime(TimeUnit.MILLISECONDS)
        }
        endTime <- Clock.currentTime(TimeUnit.MILLISECONDS)
      } yield endTime - startTime
      assertZIO[Any, Throwable, Long](timeDifference)(
        isWithin(2900L, 3100L)
      )
    } @@ TestAspect.withLiveClock,
  )

  override def spec = suite("ClientLayer")(clientLayerSpec)

}
